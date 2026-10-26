package cn.local.browsehelper;

import java.util.regex.*;

/** A small offline intent parser, not a generative model. */
public final class LocalCommand {
    public final String action, pkg, error;
    /** maxTasks == 0 means use the detected task count. */
    public final int maxTasks, minutes;
    public final boolean learnedOnly;
    private LocalCommand(String a,String p,int n,int m,boolean l,String e){action=a;pkg=p;maxTasks=n;minutes=m;learnedOnly=l;error=e;}
    public static LocalCommand parse(String raw){
        String s=raw==null?"":raw.replaceAll("\\s+","");
        if(s.matches("(请)?(停止|暂停|停下)(任务|运行|操作)?"))return new LocalCommand("stop","",0,0,false,"");
        if(s.equals("清除学习记录"))return new LocalCommand("clear","",0,0,false,"");
        if(s.contains("淘宝")&&s.contains("京东"))return bad("一次只运行一个平台，请指定淘宝或京东");
        String pkg=s.contains("淘宝")?"com.taobao.taobao":s.contains("京东")?"com.jingdong.app.mall":"";
        if(pkg.isEmpty())return bad("请指定平台，例如：淘宝浏览3个任务");
        String remaining=s.replace("淘宝","").replace("京东","");
        remaining=remaining.replaceAll("[0-9一二两三四五六七八九十]+(个任务|项任务|个|项|分钟)","");
        remaining=remaining.replaceAll("只做已学任务|只做学过的任务|只做已学会的任务|浏览|任务|帮我|请|开始|自动|完成|运行|做","");
        if(!remaining.isEmpty())return bad("暂不支持这条指令；仅支持指定平台、浏览数量或分钟数、只做已学任务");
        Matcher tasks=Pattern.compile("([0-9一二两三四五六七八九十]+)(?:个|项)").matcher(s);
        Matcher mins=Pattern.compile("([0-9一二两三四五六七八九十]+)分钟").matcher(s);
        boolean specified=tasks.find();
        int n=specified?number(tasks.group(1)):0,m=mins.find()?number(mins.group(1)):20;
        if((specified&&n<1)||m<1||m>20)return bad("任务数至少为1；运行时间为1至20分钟");
        return new LocalCommand("start",pkg,n,m,s.contains("已学")||s.contains("学过"),"");
    }
    private static int number(String s){
        try{return Integer.parseInt(s);}catch(NumberFormatException ignored){}
        s=s.replace('两','二');int t=s.indexOf('十');
        if(t>=0){int tens=t==0?1:"一二三四五六七八九".indexOf(s.charAt(0))+1;int units=t==s.length()-1?0:"一二三四五六七八九".indexOf(s.charAt(t+1))+1;return tens*10+units;}
        return s.length()==1?"一二三四五六七八九".indexOf(s.charAt(0))+1:-1;
    }
    private static LocalCommand bad(String e){return new LocalCommand("invalid","",0,0,false,e);}
}
