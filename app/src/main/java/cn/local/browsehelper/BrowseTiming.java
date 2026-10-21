package cn.local.browsehelper;

import java.util.regex.*;

/** Live countdown takes priority over the task's initial duration. */
public final class BrowseTiming {
    private static final Pattern COUNTDOWN=Pattern.compile("(?:还需|还差|剩余|再浏览|再逛|倒计时|继续浏览|浏览还剩|浏览剩余)[^0-9]{0,6}([0-9]{1,2})\\s*秒");
    private boolean observed;
    private int previous=-1;
    private long deadline;
    public static boolean requiresScroll(String text){String affirmative=text.replaceAll("(?:无需|不用|不必|不需要)(?:手动)?(?:滑动|滑一滑|上下浏览|划动|上滑|下滑|滑屏)","");return Pattern.compile("滑动|滑一滑|上下浏览|划动|上滑|下滑|滑屏").matcher(affirmative).find()&&!text.contains("滑块");}
    public static int remaining(String text){Matcher m=COUNTDOWN.matcher(text);if(!m.find())return -1;int n=Integer.parseInt(m.group(1));return n<=90?n:-1;}
    public void observe(String text,long now){int n=remaining(text);if(n<0)return;observed=true;if(n!=0||previous!=0)deadline=now+n*1000L+1500;previous=n;}
    public boolean ready(int initialSeconds,long elapsed,long now){return observed?now>=deadline:TaskRules.readyToVerify(initialSeconds,elapsed,true);}
    public boolean hasCountdown(){return observed;}
}
