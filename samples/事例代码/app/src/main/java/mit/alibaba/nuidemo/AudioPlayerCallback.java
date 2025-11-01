package mit.alibaba.nuidemo;

public interface AudioPlayerCallback {
    public void playStart();
    public void playOver();
    public void playSoundLevel(int level);
}
