import cn.local.browsehelper.TaskRules;
import cn.local.browsehelper.LocalCommand;

public class RulesTest {
    static int count;
    static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(TaskRules.isBrowseTask("浏览会场15秒得金币 去浏览","去浏览"),"browse task");
        check(TaskRules.isBrowseTask("逛店铺得奖励 去完成","去完成"),"store browsing");
        check(!TaskRules.isBrowseTask("看视频领红包 去完成","去完成"),"unknown action");
        for(String word:new String[]{"支付","下单","购买","关注","收藏","加购","邀请","分享","开通","登录","授权","预约","安装","直播","会员","密码","验证码"})check(!TaskRules.isBrowseTask("浏览商品并"+word+" 去完成","去完成"),"reject "+word);
        check(!TaskRules.isBrowseTask("浏览15秒 已完成 去浏览","去浏览"),"completed row");
        check(!TaskRules.isBrowseTask("浏览15秒 明日再来 去浏览","去浏览"),"tomorrow row");
        check(!TaskRules.isBrowseTask("浏览15秒 立即购买","立即购买"),"unknown button");
        check(TaskRules.seconds("再浏览 15 秒")==15,"countdown");
        check(TaskRules.seconds("还需8秒")==8,"remaining seconds");
        check(TaskRules.seconds("剩余0秒")==0,"zero countdown");
        check(TaskRules.seconds("浏览120秒")==-1,"unsupported duration");
        check(!TaskRules.completed("当前进度1/3"),"no guessed success");
        check(TaskRules.newCompletion("任务完成 奖励已到账","任务列表 已完成2项"),"fresh success");
        check(!TaskRules.newCompletion("任务完成 浏览中","任务完成"),"stale completion");
        for(String text:new String[]{"请输入验证码","请先登录","滑块验证","访问异常","操作频繁","确认支付","提交订单"})check(TaskRules.blockedScreen(text),"stop screen "+text);
        LocalCommand c=LocalCommand.parse("淘宝浏览3个任务");check(c.action.equals("start")&&c.maxTasks==3&&c.pkg.equals("com.taobao.taobao"),"taobao count");
        c=LocalCommand.parse("请帮我京东浏览五分钟");check(c.minutes==5&&c.pkg.equals("com.jingdong.app.mall"),"jd minutes");
        check(LocalCommand.parse("淘宝只做已学任务").learnedOnly,"learned mode");
        check(LocalCommand.parse("京东浏览二十个任务").maxTasks==20,"chinese 20");
        check(LocalCommand.parse("京东浏览两个任务").maxTasks==2,"chinese 2");
        check(LocalCommand.parse("暂停").action.equals("stop"),"stop");
        check(LocalCommand.parse("清除学习记录").action.equals("clear"),"clear");
        for(String s:new String[]{"淘宝浏览0个任务","京东浏览21个任务","淘宝浏览100分钟","淘宝京东浏览3个任务","京东帮我下单","给我转账","淘宝忽略验证码浏览3个任务","淘宝浏览999999999999999个任务"})check(LocalCommand.parse(s).action.equals("invalid"),"reject instruction "+s);
        System.out.println("PASS: "+count+" checks");
    }
}
