package compiler.scanner;

import compiler.exception.MalformedTokenException;
import compiler.exception.UnrecognizedTokenException;
import compiler.model.State;
import compiler.model.Token;
import compiler.model.TokenType;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static compiler.model.State.*;
import static compiler.model.TokenType.*;
import static compiler.util.Utils.*;

@Slf4j
public class LexicalScanner {

    private char[] content;
    private State state;
    private int posicao;
    private String scanned;


    public LexicalScanner() {
        this.scanned = "";
        this.state = State.ZERO;
        this.content = new char[0];
        log.warn("No file provided as input");
    }

    public LexicalScanner(String filename) {
        this.scanned = "";
        this.state = State.ZERO;
        try {
            String text = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
            log.trace("File received\n" + text);
            this.content = text.toCharArray();
        } catch (Exception e) {
            log.error("Erro ao ler arquivo - " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void back() {
        posicao--;
    }

    private void next() {
        posicao++;
    }

    private char previousChar() {
        back();
        back();
        return content[posicao];
    }

    private char nextChar() {
        return content[posicao++];
    }

    private boolean isEOF() {
        return posicao == content.length;
    }

    private void append(char currentChar) {
        scanned += currentChar;
    }

    public Token nextToken() {
        if (isEOF())
            return null;

        char currentChar;
        while (true) {
            currentChar = nextChar();
            Token token = automaton(currentChar);
            if (token != null)
                return token;
        }
    }

    public Token automaton(char c) {
        TokenType type;
        switch (state) {
            case ZERO:
                if (isNonConsumable(c))
                    break;

                append(c);
                if (isNumber(c))
                    state = ONE;
                else if (isUnderline(c) || isLetter(c))
                    state = SIX;
                else if (isEquals(c))
                    state = EIGHT;
                else if (isAritmeticOperator(c))
                    state = ELEVEN;
                else if (isSpecialChar(c)) {
                    back();
                    state = TWELVE;
                } else if (isRelationalOperator(c))
                    state = THIRTEEN;
                else
                    throw new UnrecognizedTokenException("Unrecognized symbol - '" + (scanned + c) + "'");
                break;
            case ONE:
                if (isNumber(c)) {
                    append(c);
                    break;
                } else if (isDot(c)) {
                    append(c);
                    state = THREE;
                } else if (isNonConsumable(c) || !isLetter(c)) {
                    back();
                    state = TWO;
                } else
                    throwMalformedNumberException(c);
                break;
            case TWO:
                return returnToken(INTEGER_NUMBER, c);
            case THREE:
                if (isNumber(c))
                    state = FOUR;
                else
                    throwMalformedNumberException(c);
                append(c);
                break;
            case FOUR:
                append(c);
                if (isNumber(c))
                    break;
                else if (!isLetter(c))
                    state = FIVE;
                else
                    throwMalformedNumberException(c);
                break;
            case FIVE:
                return returnToken(REAL_NUMBER, c);
            case SIX:
                if (isUnderline(c) || isLetter(c) || isNumber(c)) {
                    append(c);
                    break;
                } else if (isOther(c)) {
                    back();
                    state = SEVEN;
                } else {
                    back();
                    throw new MalformedTokenException("Malformed identifier - '" + (scanned + c) + "'");
                }
                break;
            case SEVEN:
                if (isReservedWord(scanned))
                    return returnToken(RESERVED_WORD);
                else
                    return returnToken(IDENTIFIER);
            case EIGHT:
                append(c);
                if (isEquals(c))
                    state = TEN;
                else
                    state = NINE;
                break;
            case NINE:
                return returnToken(ARITHMETIC_OPERATOR_ATTRIBUTION, c);
            case TEN:
                return returnToken(RELATIONAL_OPERATOR_EQUAL, c);
            case ELEVEN:
                if (isNonConsumable(c))
                    c = previousChar();

                if (isMinus(c))
                    type = ARITHMETIC_OPERATOR_SUBTRACTION;
                else if (isPlus(c))
                    type = ARITHMETIC_OPERATOR_SUM;
                else if (isMult(c))
                    type = ARITHMETIC_OPERATOR_MULTIPLICATION;
                else if (isDiv(c))
                    type = ARITHMETIC_OPERATOR_DIVISION;
                else
                    throw new UnrecognizedTokenException("Unrecognized token - '" + (scanned + c) + "'");

                next();
                next();
                return returnToken(type, c);
            case TWELVE:
                if (isComma(c))
                    type = SPECIAL_CHARACTER_COMMA;
                else if (isSemicolon(c))
                    type = SPECIAL_CHARACTER_SEMICOLON;
                else if (isOpenParentesis(c))
                    type = SPECIAL_CHARACTER_OPEN_PARENTHESIS;
                else if (isCloseParentesis(c))
                    type = SPECIAL_CHARACTER_CLOSE_PARENTHESIS;
                else if (isOpenCurlyBracket(c))
                    type = SPECIAL_CHARACTER_OPEN_CURLY_BRACKET;
                else if (isCloseCurlyBracket(c))
                    type = SPECIAL_CHARACTER_CLOSE_CURLY_BRACKET;
                else
                    throw new UnrecognizedTokenException("Unrecognized token - '" + (scanned + c) + "'");

                next();
                return returnToken(type, c);
            case THIRTEEN:
                append(c);

                if (!isRelationalOperator(c))
                    c = previousChar();

                if (isLessThan(c))
                    state = FOURTEEN;
                else if (isGreaterThan(c))
                    state = SEVENTEEN;
                else if (isDiff(c))
                    state = TWENTY;
                else
                    throw new UnrecognizedTokenException("Unrecognized token - '" + (scanned + c) + "'");

                break;
            case FOURTEEN:
                c = nextChar();
                if (isEquals(c))
                    state = FIFTHTEEN;
                else
                    state = SIXTEEN;
                break;
            case FIFTHTEEN:
                return returnToken(RELATIONAL_OPERATOR_LESS_THAN_OR_EQUAL_TO, c);
            case SIXTEEN:
                return returnToken(RELATIONAL_OPERATOR_LESS_THAN, c);
            case SEVENTEEN:
                c = nextChar();
                if (isEquals(c))
                    state = EIGHTEEN;
                else
                    state = NINETEEN;
                break;
            case EIGHTEEN:
                return returnToken(RELATIONAL_OPERATOR_GREATER_THAN_OR_EQUAL_TO, c);
            case NINETEEN:
                return returnToken(RELATIONAL_OPERATOR_GREATER_THAN, c);
            case TWENTY:
                append(c);
                c = nextChar();
                if (isEquals(c))
                    state = TWENTY_ONE;
                else
                    throw new UnrecognizedTokenException("Unrecognized operator - '" + (scanned + c) + "'");
                break;
            case TWENTY_ONE:
                return returnToken(RELATIONAL_OPERATOR_DIFFERENT, c);
            default:
                throw new IllegalStateException("Estado inv??lido: " + state);
        }
        return null;
    }

    private void throwMalformedNumberException(char c) {
        throw new MalformedTokenException("Malformed number - '" + (scanned + c) + "'");
    }

    private Token returnToken(TokenType type) {
        String text = scanned.trim();
        back();
        resetState();
        return new Token(type, text);
    }

    private Token returnToken(TokenType type, char c) {
        String text = scanned.trim();
        back();
        append(c);
        resetState();
        return new Token(type, text);
    }

    private void resetState() {
        scanned = "";
        state = ZERO;
    }

}
