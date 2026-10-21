package cn.local.browsehelper;

import java.util.regex.*;

/** Live countdown takes priority over the task's initial duration. */
public final class BrowseTiming {
    private static final Pattern COUNTDOWN=Pattern.compile("(?:还需|还差|剩余|再浏览|再逛|倒计时|继续浏览|浏览还剩|浏览剩余)[^0-9]{0,6}([0-9]{1,2})\\s*秒");
    private boolean observed;
    private int previous=-1;
    private long deadline;
    private static final String SCROLL="上下滑动|上下滑|向上滑动|向下滑动|向上滑|向下滑|往上滑|往下滑|向上划|向下划|往上划|往下划|上划|下划|滑一滑|滑一下|上下浏览|划动|上滑|下滑|滑屏|滑动|滚动(?:页面|屏幕|浏览)|滚一滚|上下拖动|向上拖动|向下拖动|(?:上拉|下拉)浏览|滑到(?:页面)?底部";
    public static boolean requiresScroll(String text){
        String clean=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).replaceAll("\\s+","");
        String affirmative=clean.replaceAll("(?:无需|不用|不必|不需要|不要|禁止|不能)(?:再|手动|进行)*(?:"+SCROLL+")","");
        return Pattern.compile(SCROLL).matcher(affirmative).find()&&!clean.contains("滑块");
    }
    public static int remaining(String text){Matcher m=COUNTDOWN.matcher(text);if(!m.find())return -1;int n=Integer.parseInt(m.group(1));return n<=90?n:-1;}
    public void observe(String text,long now){int n=remaining(text);if(n<0)return;observed=true;if(n!=0||previous!=0)deadline=now+n*1000L+1500;previous=n;}
    public boolean ready(int initialSeconds,long elapsed,long now){return observed?now>=deadline:TaskRules.readyToVerify(initialSeconds,elapsed,true);}
    public boolean hasCountdown(){return observed;}
}
