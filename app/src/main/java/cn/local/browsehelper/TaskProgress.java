package cn.local.browsehelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Task-list evidence, never elapsed time, is used to confirm repeatable tasks. */
public final class TaskProgress {
    private static final Pattern COUNTER=Pattern.compile("[（(]\\s*([0-9]{1,3})\\s*/\\s*([0-9]{1,3})\\s*[）)]");
    public final String name;
    public final int current,total,seconds;
    private TaskProgress(String n,int c,int t,int s){name=n;current=c;total=t;seconds=s;}
    public static TaskProgress parse(String row){
        Matcher m=COUNTER.matcher(row);
        if(!m.find())return null;
        String name=row.substring(0,m.start()).replaceAll("\\s+","");
        int c=Integer.parseInt(m.group(1)),t=Integer.parseInt(m.group(2));
        if(name.isEmpty()||name.length()>96||t<1||t>100||c>t||m.find())return null;
        return new TaskProgress(name,c,t,TaskRules.seconds(row));
    }
    public boolean incomplete(){return current<total;}
    public static boolean containsCounter(String row){return COUNTER.matcher(row).find();}
    public boolean advancedFrom(TaskProgress previous){return previous!=null&&name.equals(previous.name)&&total==previous.total&&current>previous.current;}
    public String identity(){return "progress-v1:"+name;}
    public String attemptKey(){return identity()+":"+current+"/"+total;}
    /** A timed return is only a request to verify; it is never a success signal. */
    public boolean readyToCheck(long elapsedMs,boolean leftTaskList){return incomplete()&&seconds>0&&leftTaskList&&elapsedMs>=(seconds+4)*1000L;}
}
