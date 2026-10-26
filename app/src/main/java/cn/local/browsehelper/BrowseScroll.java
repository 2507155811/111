package cn.local.browsehelper;

/** Slow, repeated browsing strokes; lifetime is bounded by the task/run timeout. */
public final class BrowseScroll {
    public static final int DURATION_MS=2200;
    public static final int GAP_MS=250;
    public static boolean due(boolean browsing,boolean required,long browseElapsed,long sinceLastStart){
        return browsing&&required&&browseElapsed>=1200&&sinceLastStart>=DURATION_MS+GAP_MS;
    }
    public static boolean fallback(boolean enabled,boolean browsing,boolean forbidden,long elapsed){
        return enabled&&browsing&&!forbidden&&elapsed>=2500;
    }
    public static boolean upward(int strokes){return strokes%2==0;}
    private BrowseScroll(){}
}
