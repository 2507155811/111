import cn.local.browsehelper.TaskRules;
import cn.local.browsehelper.LocalCommand;
import cn.local.browsehelper.TaskProgress;
import cn.local.browsehelper.NavigationRules;

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
        check(TaskRules.isBrowseTask("好物沉浸看(0/10) 浏览15秒 +400 去完成","去完成"),"screenshot immersive browse");
        check(TaskRules.isBrowseTask("好物随心逛(0/10) 浏览15秒 +400 去完成","去完成"),"screenshot casual browse");
        check(TaskRules.isBrowseTask("去芭芭农场领水果(0/1) 浏览5秒 +600 去完成","去完成"),"screenshot farm browse");
        check(!TaskRules.isBrowseTask("预约组队开赛提醒(0/1) 成功订阅组队开赛提醒得+1000 去完成","去完成"),"screenshot subscription");
        check(!TaskRules.isBrowseTask("逛一逛蚂蚁庄园(0/1) 逛一逛蚂蚁庄园+800 去完成","去完成"),"screenshot cross-app unknown");
        check(!TaskRules.isBrowseTask("逛一逛支付宝农场(0/1) 点击去逛+600 去完成","去完成"),"screenshot alipay");
        check(!TaskRules.isBrowseTask("淘金币省钱等级考试(0/1) 去答题最高得+1000 去完成","去完成"),"screenshot quiz");
        check(!TaskRules.isBrowseTask("搜一搜你心仪的宝贝(0/5) 浏览15秒+400 去完成","去完成"),"screenshot search");
        for(String page:new String[]{"福利","精选","推荐","好物"}){
            String row="浏览6秒"+page+"页面 去完成";
            check(TaskRules.isBrowseTask(row,"去完成"),"jd dynamic name "+page);
            check(TaskRules.seconds(row)==6,"jd duration "+page);
        }
        TaskProgress p=TaskProgress.parse("好物沉浸看(0/10) 浏览15秒 +400 去完成");
        check(p!=null&&p.current==0&&p.total==10&&p.seconds==15,"extract progress");
        TaskProgress next=TaskProgress.parse("好物沉浸看（1/10） 浏览15秒 +400 去完成");
        check(next.advancedFrom(p),"increment counts");
        check(p.identity().equals(next.identity()),"stable learned identity");
        check(!p.attemptKey().equals(next.attemptKey()),"repeat action after increment allowed");
        check(!p.advancedFrom(p),"unchanged is not success");
        check(!p.advancedFrom(next),"decreased is not success");
        check(!TaskProgress.parse("好物随心逛(1/10) 浏览15秒 去完成").advancedFrom(p),"other row cannot confirm");
        check(!TaskProgress.parse("好物沉浸看(1/20) 浏览15秒 去完成").advancedFrom(p),"changed task rules cannot confirm");
        check(TaskProgress.parse("好物沉浸看(11/10) 浏览15秒")==null,"corrupt progress");
        check(TaskProgress.parse("好物沉浸看(0/0) 浏览15秒")==null,"zero total");
        check(TaskProgress.parse("任务A(0/10) 任务B(0/5)")==null,"multiple task rows rejected");
        check(!TaskRules.isBrowseTask("好物沉浸看(10/10) 浏览15秒 去完成","去完成"),"finished repeated task");
        check(!TaskRules.isBrowseTask("好物沉浸看(11/10) 浏览15秒 去完成","去完成"),"invalid task counter not eligible");
        check(!p.readyToCheck(15000,true),"buffer before verification");
        check(p.readyToCheck(19000,true),"timed verification only");
        check(!p.readyToCheck(30000,false),"failed navigation cannot count elapsed time");
        check(!TaskRules.readyToVerify(-1,100000,true),"unknown timer cannot return on time alone");
        check(!TaskRules.readyToVerify(6,9000,true),"jd six seconds plus buffer");
        check(TaskRules.readyToVerify(6,10000,true),"jd verification after buffer");
        check(TaskRules.rowCompleted("浏览6秒精选页面 已达成"),"jd completed label");
        check(!TaskRules.rowCompleted("浏览6秒精选页面 未达成"),"jd not completed");
        // Simulate all ten iterations: only a larger returned counter advances.
        for(int i=0;i<10;i++){
            TaskProgress a=TaskProgress.parse("每日随机会场("+i+"/10) 浏览15秒 去完成");
            TaskProgress b=TaskProgress.parse("每日随机会场("+(i+1)+"/10) 浏览15秒 去完成");
            check(a.incomplete()&&a.readyToCheck(19000,true)&&b.advancedFrom(a),"repeat cycle "+i);
        }
        check(!TaskProgress.parse("每日随机会场(10/10) 浏览15秒").incomplete(),"repeat loop terminates");
        check(NavigationRules.isTaskList("做任务赚能量 好物沉浸看(0/10) 浏览15秒 去完成"),"taobao list");
        check(NavigationRules.isTaskList("完成任务打卡 领红包 浏览6秒福利页面 去完成"),"jd list");
        check(NavigationRules.isTaskList("随机会场 浏览5秒 去完成 商城 浏览15秒 去完成"),"generic list");
        check(!NavigationRules.isTaskList("任务完成 去完成 浏览商品 还需15秒"),"completion widget must not block return timer");
        check(!NavigationRules.isTaskList("浏览任务 去完成 请上下滑动浏览15秒"),"browse page not a list");
        check(!NavigationRules.isTaskList("升级赛车拿钱 去赚能量"),"activity landing page");
        check(NavigationRules.isEntry("去赚能量")&&NavigationRules.isEntry("做任务"),"reopen task entries");
        check(!NavigationRules.isEntry("立即支付")&&!NavigationRules.isEntry("领红包"),"entry whitelist");
        // Regression: a browse widget containing task/action text used to prevent leaving phase 1.
        String page="浏览任务 去完成 还需6秒 上下滑动浏览";
        boolean left=!NavigationRules.isTaskList(page);
        check(left&&TaskRules.readyToVerify(6,10000,left),"jd widget can trigger timed return verification");
        check(!TaskRules.readyToVerify(6,9999,left),"no early return");
        System.out.println("PASS: "+count+" checks");
    }
}
