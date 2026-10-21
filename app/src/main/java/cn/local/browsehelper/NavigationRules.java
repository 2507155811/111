package cn.local.browsehelper;

/** A task-completion widget on a browsing page is not a task list. */
public final class NavigationRules {
    public static boolean isTaskList(String text){
        int actions=count(text,"去完成")+count(text,"去浏览")+count(text,"去逛逛");
        boolean heading=text.contains("做任务赚能量")||text.contains("累计任务奖励")||text.contains("完成任务打卡")||text.contains("任务列表")||text.contains("每日任务")||text.contains("赚步数");
        return (heading&&(actions>0||TaskRules.rowCompleted(text)))||actions>=2;
    }
    public static boolean isEntry(String text){return text.equals("去赚能量")||text.equals("赚步数")||text.equals("赚能量")||text.equals("做任务")||text.equals("去做任务")||text.equals("任务列表");}
    private static int count(String text,String token){int n=0,p=0;while((p=text.indexOf(token,p))>=0){n++;p+=token.length();}return n;}
    private NavigationRules(){}
}
