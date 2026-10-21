package cn.local.browsehelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure rules: unknown or potentially consequential tasks are never clicked. */
public final class TaskRules {
    private static final Pattern SECONDS = Pattern.compile("(?:浏览|停留|逛|再浏览|剩余|还需|倒计时|再逛)[^0-9]{0,8}([0-9]{1,3})\\s*秒|([0-9]{1,3})\\s*秒");
    private static final Pattern BLOCK = Pattern.compile("支付|付款|下单|购买|开通|充值|邀请|好友|分享|助力|加购|购物车|收藏|关注|授权|登录|登陆|签到|抽奖|领券|兑换|订阅|预约|绑定|入会|会员|评价|评论|直播|下载|安装|验证码|滑块|安全验证|人脸|指纹|密码|答题|考试|搜一搜|搜索");
    public static boolean isBrowseTask(String row, String button) {
        if (row == null || button == null || row.length() > 220 || BLOCK.matcher(row).find()) return false;
        boolean action = button.equals("去浏览") || button.equals("去完成") || button.equals("逛一逛") || button.equals("去逛逛");
        TaskProgress progress=TaskProgress.parse(row);
        if(TaskProgress.containsCounter(row)&&progress==null)return false;
        return action && row.contains("浏览") && !rowCompleted(row) && (progress==null||progress.incomplete());
    }
    public static boolean blockedScreen(String text) {
        return Pattern.compile("请.{0,4}(登录|登陆)|验证码|安全验证|滑块|拖动.{0,8}(滑块|拼图)|确认支付|立即支付|提交订单|人脸识别|输入密码|访问异常|操作频繁").matcher(text).find();
    }
    public static int seconds(String text) {
        Matcher m = SECONDS.matcher(text);
        if (!m.find()) return -1;
        int n = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
        return n <= 90 ? n : -1;
    }
    public static boolean completed(String text) {
        return text.contains("浏览完成") || text.contains("任务已完成") || text.contains("任务完成") || text.contains("已完成浏览") || text.contains("奖励已到账") || text.contains("恭喜完成");
    }
    public static boolean newCompletion(String current,String before){
        for(String s:new String[]{"浏览完成","任务已完成","任务完成","已完成浏览","奖励已到账","恭喜完成"})if(current.contains(s)&&!before.contains(s))return true;
        return false;
    }
    public static boolean rowCompleted(String row){return row.contains("已完成")||row.contains("已领取")||row.contains("已达成")||row.contains("已浏览")||row.contains("明日再来")||row.contains("已打卡");}
    public static boolean readyToVerify(int seconds,long elapsedMs,boolean leftList){return seconds>0&&seconds<=90&&leftList&&elapsedMs>=(seconds+4)*1000L;}
    public static String manualReason(String text){
        if(text.contains("预约")||text.contains("订阅"))return "预约/订阅任务需手动确认";
        if(text.contains("答题")||text.contains("考试"))return "答题任务需手动处理";
        if(text.contains("支付宝")||text.contains("蚂蚁庄园"))return "跨应用任务需手动处理";
        if(text.contains("搜索")||text.contains("搜一搜"))return "搜索任务需手动处理";
        return "其余任务不符合明确的浏览条件";
    }
    private TaskRules() {}
}
