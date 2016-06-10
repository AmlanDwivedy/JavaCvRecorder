package co.gdera.javacvtest.utils;

/**
 * Created by amlan on 10/6/16.
 */
public class SavedFrames {
    private byte[] data;
    private long timeStamp;

    public SavedFrames(byte[] data, long timeStamp) {
        this.data = data;
        this.timeStamp = timeStamp;
    }

    public byte[] getFrameBytesData() {
        return data;
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}
