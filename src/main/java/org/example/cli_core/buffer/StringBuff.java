package org.example.cli_core.buffer;

public class StringBuff extends Buff<String> {
    public StringBuff(String[] source) {
        super(source);
    }

    /**
     *
     * @param size 緩衝區尺寸
     * @throws NegativeArraySizeException 如果指定的 size參數中的任何 size為負。
     */
    public StringBuff(int size) throws NegativeArraySizeException{
        super(new String[size]);
    }
}
