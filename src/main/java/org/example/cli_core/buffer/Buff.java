package org.example.cli_core.buffer;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.InvalidMarkException;

public class Buff<T> implements Iterable<T>{
    private final T[] sourceArray;
    int position;
    int mark;
    int limit;

    public Buff(T[] source){
        if(source == null)
            throw new NullPointerException();

        this.sourceArray = source;
        this.position = 0;
        this.mark = -1;
        this.limit = this.sourceArray.length;
    }

    /**
     * 返回支持此緩衝區的數組（可選操作） 。
     * 該方法旨在使陣列支持的緩衝區更有效地傳遞到本地代碼。 具體的子類為此方法提供了更強類型的返回值。
     * 對此緩衝區內容的修改將導致返回的數組的內容被修改，反之亦然。
     * 在調用此方法之前調用hasArray方法，以確保此緩衝區具有可訪問的後台陣列。
     *
     * @return 支持這個緩衝區的數組
     */
    public T[] array(){
        return this.sourceArray;
    }

    /**
     * 返回此緩衝區的容量。
     *
     * @return 這個緩衝區的容量
     */
    public int capacity(){
        return this.sourceArray.length;
    }

    /**
     * 清除此緩衝區。 位置設置為零，限制設置為容量，標記被丟棄。
     * 在使用一系列通道讀取或放置操作填充此緩衝區之前調用此方法。 例如：
     * <p>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data
     * <p>
     * 這個方法實際上並不會清除緩衝區中的數據，但是它被命名為它的確是因為它最常用於情況也是如此。
     *
     * @return 這個緩衝區
     */
    public Buff<T> clear(){
        this.mark = 0;
        this.position = 0;
        this.limit = this.capacity();
        return this;
    }

    /**
     * 翻轉這個緩衝區。 該限制設置為當前位置，然後將該位置設置為零。 如果標記被定義，則它被丟棄。
     * 在通道讀取或放置操作的序列之後，調用此方法來準備一系列通道寫入或相對獲取操作。 例如：
     * <p>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel
     * <p>
     * 當將數據從一個地方傳輸到另一個地址時，該方法通常與compact方法結合使用。
     *
     * @return 這個緩衝區
     */
    public Buff<T> flip(){
        this.limit = this.position;
        this.position = 0;
        this.mark = -1;
        return this;
    }

    /**
     * 返回此緩衝區的限制。
     *
     * @return 這個緩衝區的極限
     */
    public int limit(){
        return this.limit;
    }

    /**
     * 設置此緩衝區的限制。 如果位置大於新限制，那麼它被設置為新限制。 如果標記被定義並且大於新限制，則它被丟棄。
     *
     * @param newLimit 新限制值; 必須是非負數，不大於此緩衝區的容量
     * @return 這個緩衝區
     * @throws IllegalArgumentException 如果newLimit的 前提條件不成立
     */
    public Buff<T> limit(int newLimit) throws IllegalArgumentException{
        if((newLimit > this.sourceArray.length) || (newLimit < 0))
            throw new IllegalArgumentException();

        this.limit = newLimit;
        return this;
    }

    /**
     * 將此緩衝區的標記設置在其位置。
     *
     * @return 這個緩衝區
     */
    public Buff<T> mark(){
        this.mark = position;
        return this;
    }

    /**
     * 設置這個緩衝區的位置。 如果標記被定義並且大於新位置，則它被丟棄。
     *
     * @param newPosition 新的位置值; 必須是非負數，不得大於當前限制
     * @return 這個緩衝區
     * @throws IllegalArgumentException 如果newPosition的 前提條件不成立
     */
    public Buff<T> position(int newPosition) throws IllegalArgumentException{
        if((newPosition > this.sourceArray.length) || (newPosition < 0))
            throw new IllegalArgumentException();

        this.position = newPosition;
        return this;
    }

    /**
     * 返回當前位置和限制之間的元素數。
     *
     * @return 此緩衝區中剩餘的元素數
     */
    public int remaining(){
        return (this.limit - this.position);
    }

    /**
     * 將此緩衝區的位置重置為先前標記的位置。
     * 調用此方法既不會更改也不丟棄該標記的值。
     *
     * @return 這個緩衝區
     * @throws InvalidMarkException 如果標記尚未設置
     */
    public Buff<T> reset() throws InvalidMarkException{
        if(this.mark < 0)
            throw new InvalidMarkException();

        this.position = mark;
        return this;
    }

    /**
     * 倒帶這個緩衝區。 位置設置為零，標記被丟棄。
     * 在通道寫入或獲取操作的序列之前調用此方法，假設已經設置了相應的限制。 例如：
     * <p>
     *      out.write(buf);    // Write remaining data
     *      buf.rewind();      // Rewind buffer
     *      buf.get(array);    // Copy data into array
     * <p>
     *
     * @return 這個緩衝區
     */
    public Buff<T> rewind(){
        this.mark = -1;
        this.position = 0;
        return this;
    }

    /**
     * 相對獲取方法。 讀取該緩衝區當前位置的字節，然後增加位置。
     *
     * @return 緩衝區當前位置的字節
     * @throws BufferUnderflowException 如果緩衝區的當前位置不小於其限制
     */
    public T get() throws BufferUnderflowException {
        if(this.position >= this.limit)
            throw new BufferUnderflowException();

        return this.sourceArray[this.position++];
    }

    /**
     * 相對放置法（可選操作） 。
     * 將給定字節寫入當前位置的緩衝區，然後增加位置。
     *
     * @param t 要寫入的元素
     * @return 這個緩衝區
     * @throws BufferOverflowException 如果此緩衝區的當前位置不小於其限制
     */
    public Buff<T> put(T t) throws BufferOverflowException {
        if(this.position >= this.limit)
            throw new BufferOverflowException();

        this.sourceArray[this.position++] = t;
        return this;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>(this);
    }

    public static class Iterator<T> implements java.util.Iterator<T> {
        private final Buff<T> buffer;
        public Iterator(Buff<T> buffer){
            this.buffer = buffer;
        }

        @Override
        public boolean hasNext() {
            return (buffer.remaining() != 0);
        }

        @Override
        public T next() {
            return buffer.get();
        }
    }
}
