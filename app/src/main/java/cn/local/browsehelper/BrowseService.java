package cn.local.browsehelper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.*;
import java.util.*;

public class BrowseService extends AccessibilityService {
    public static BrowseService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String target="", status="未开始", before="", taskId="";
    private boolean running, busy;
    private boolean leftTaskList, reopened;
    private TaskProgress activeProgress, verifiedProgress;
    private String pendingEntry="";
    private int confirmVisited;
    private int expectedSeconds=-1;
    private int listScrolls;
    private boolean pendingScroll;
    private String lastList="";
    private final Set<String> seenLists=new HashSet<>();
    private int phase, count, scanned;
    private long since, taskSince, returnSince;
    private long runSince;
    private long browseSince;
    private long unreadableSince;
    private long lastSwipe;
    private int browseSwipes, backAttempts;
    private boolean clickFallbackUsed;
    private Rect taskButton;
    private int maxTasks=0, maxMinutes=20;
    private int requestLimit=0;
    private boolean learnedOnly;
    private boolean autoDiscover;
    private long nextDiscoveryAt;
    private String version="";
    private LinearLayout overlay;
    private TextView message;
    private Button toggle;
    private Button markScroll;
    private final Set<String> attempted=new HashSet<>();
    private BrowsePlan approved=new BrowsePlan();
    private String deferredTask="";
    private long nextTaskAt;
    private boolean timedOut;
    private BrowseTiming timing=new BrowseTiming();
    private boolean needsScroll;
    private boolean previewing;
    private List<Candidate> collecting;
    private final Set<String> scanExcluded=new HashSet<>();
    private LinearLayout confirmOverlay;
    private int previewChars,previewActions,scanWindows;
    private long previewElapsed;
    private final Runnable pulse=new Runnable(){public void run(){try{tick();}catch(RuntimeException e){pause("页面读取异常，请重新打开任务页");}if(running)handler.postDelayed(this,phase==1?1000:250);}};
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;}
    public String statusText(){return status;}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(!running||phase!=1||!target.equals(String.valueOf(e.getPackageName())))return;
        StringBuilder s=new StringBuilder();for(CharSequence t:e.getText())if(t!=null&&s.length()<4000)s.append(t).append(' ');
        if(e.getContentDescription()!=null)s.append(e.getContentDescription());
        String prompt=s.toString();
        if(TaskRules.blockedScreen(prompt)){pause("检测到验证或敏感提示，已暂停");return;}
        if(BrowseTiming.requiresScroll(prompt)){needsScroll=true;setStatus("从页面提示识别到滑动要求，进入浏览页后执行");}
    }
    @Override public void onInterrupt(){closeConfirmation();pause("服务中断，请重新检查页面");}
    @Override public void onDestroy(){dismiss();instance=null;super.onDestroy();}
    @Override public boolean onUnbind(Intent i){dismiss();instance=null;return super.onUnbind(i);}
    public boolean prepare(String pkg){
        dismiss();target=pkg;attempted.clear();approved=new BrowsePlan();count=0;maxTasks=0;requestLimit=0;maxMinutes=20;learnedOnly=false;
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(pkg,0);version=String.valueOf(android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);}catch(Exception e){return false;}
        overlay=new LinearLayout(this);overlay.setOrientation(1);overlay.setPadding(dp(12),dp(8),dp(12),dp(8));overlay.setBackgroundColor(Color.rgb(20,39,51));
        message=new TextView(this);message.setTextColor(Color.WHITE);message.setTextSize(12);overlay.addView(message,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);
        toggle=new Button(this);toggle.setTextSize(12);toggle.setText("扫描确认");toggle.setOnClickListener(v->{if(running)pause("已停止，请重新扫描确认");else{setStatus("正在扫描当前任务页，请稍候…");handler.postDelayed(()->{try{preview();}catch(RuntimeException e){closeConfirmation();setStatus("扫描异常："+e.getClass().getSimpleName()+"；请重新开启无障碍服务后重试");}},80);}});actions.addView(toggle,new LinearLayout.LayoutParams(0,dp(44),1));
        markScroll=new Button(this);markScroll.setText("需滑动");markScroll.setTextSize(11);markScroll.setEnabled(false);markScroll.setOnClickListener(v->{
            if(!running||phase!=1||taskId.isEmpty())return;
            needsScroll=true;getSharedPreferences("scroll-overrides",0).edit().putBoolean(learnKey(taskId),true).apply();
            setStatus("已记住本任务需要滑动；其他任务不受影响");
        });actions.addView(markScroll,new LinearLayout.LayoutParams(0,dp(44),1));
        Button close=new Button(this);close.setText("关闭");close.setTextSize(12);close.setOnClickListener(v->dismiss());actions.addView(close,new LinearLayout.LayoutParams(0,dp(44),1));overlay.addView(actions);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(232),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;p.y=dp(100);p.x=dp(8);
        message.setOnTouchListener(new android.view.View.OnTouchListener(){float downX,downY;int oldX,oldY;public boolean onTouch(android.view.View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();oldX=p.x;oldY=p.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){p.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-dp(232),oldX-(int)(e.getRawX()-downX)));p.y=Math.max(dp(24),Math.min(getResources().getDisplayMetrics().heightPixels-dp(150),oldY+(int)(e.getRawY()-downY)));try{((WindowManager)getSystemService(WINDOW_SERVICE)).updateViewLayout(overlay,p);}catch(RuntimeException ignored){}return true;}return true;}});
        try{((WindowManager)getSystemService(WINDOW_SERVICE)).addView(overlay,p);}catch(RuntimeException e){overlay=null;return false;}
        setStatus("先手动打开活动任务列表，再点开始");return true;
    }
    public void configure(LocalCommand c){maxTasks=c.maxTasks;requestLimit=c.maxTasks;maxMinutes=c.minutes;learnedOnly=c.learnedOnly;setStatus("打开任务列表后扫描确认 · "+(requestLimit>0?"最多"+requestLimit+"次":"自动检测任务数量")+" / "+maxMinutes+"分钟"+(learnedOnly?" · 仅已学任务":""));}
    private void preview(){
        long scanStarted=SystemClock.elapsedRealtime();
        closeConfirmation();AccessibilityNodeInfo root=applicationRoot();
        if(root==null){setStatus("没有可读页面，请先打开任务列表");return;}
        final List<BrowsePlan.Item> items=new ArrayList<>();
        try{
            if(!target.equals(String.valueOf(root.getPackageName()))){setStatus("请先打开所选应用的任务列表");return;}
            scanned=0;String text=flatten(root,0);
            previewChars=text.length();previewActions=TaskRows.actionCount(text);
            if(TaskRules.blockedScreen(text)){setStatus("请先手动处理验证或登录");return;}
            for(Candidate c:scanCandidates(root,Collections.emptySet())){
                try{items.add(new BrowsePlan.Item(c.key,c.rowText));}finally{c.node.recycle();}
            }
        }finally{previewing=false;scanExcluded.clear();root.recycle();previewElapsed=SystemClock.elapsedRealtime()-scanStarted;}
        if(items.isEmpty()){setStatus("可执行任务0 · 窗口"+scanWindows+"个 / 可读"+previewChars+"字 / 按钮"+previewActions+"个；"+(previewActions>0?"已读到按钮，但未找到符合浏览/逛直播间规则的完整任务行":"未读到任务按钮，请反馈此提示"));return;}
        showConfirmation(items);
    }
    private TextView panelText(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextColor(Color.WHITE);t.setTextSize(size);t.setPadding(0,dp(5),0,dp(5));return t;}
    private void showConfirmation(final List<BrowsePlan.Item> items){
        confirmOverlay=new LinearLayout(this);confirmOverlay.setOrientation(1);confirmOverlay.setPadding(dp(18),dp(14),dp(18),dp(14));confirmOverlay.setBackgroundColor(Color.rgb(20,39,51));
        confirmOverlay.addView(panelText("确认浏览任务",21));
        confirmOverlay.addView(panelText("连续执行已开启：完成后返回并自动下一个，无需逐项点击开始。",13));
        confirmOverlay.addView(panelText("仅统计当前可见页面，非活动全部任务。任务数量随页面更新；取消勾选的条目不会自动加入。",13));
        confirmOverlay.addView(panelText("无次数标记的条目本轮最多1次。预约、答题、搜索、加购、关注、支付等非浏览任务不执行。",12));
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);
        List<CheckBox> checks=new ArrayList<>(),slideChecks=new ArrayList<>();
        for(BrowsePlan.Item item:items){
            CheckBox check=new CheckBox(this);check.setTextColor(Color.WHITE);check.setTextSize(14);check.setChecked(true);
            check.setText(item.title+"\n剩余 "+item.remaining+" 次 · "+(item.seconds>0?item.seconds+"秒浏览":"时长未知，需明确完成提示"));
            checks.add(check);rows.addView(check);
            CheckBox slide=new CheckBox(this);slide.setTextColor(Color.LTGRAY);slide.setTextSize(12);slide.setText("本项要求滑动（只有箭头/图片提示时可指定）");slide.setChecked(getSharedPreferences("scroll-overrides",0).getBoolean(learnKey(item.id),false));slideChecks.add(slide);rows.addView(slide);
        }
        confirmOverlay.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(260),dp(70)*items.size())));
        TextView totals=panelText("",14);confirmOverlay.addView(totals);
        TextView limitText=panelText("",14);confirmOverlay.addView(limitText);
        CheckBox discover=new CheckBox(this);discover.setTextColor(Color.WHITE);discover.setTextSize(12);
        discover.setText("返回后自动检测并继续后续纯浏览任务（排除取消勾选项）");discover.setChecked(true);confirmOverlay.addView(discover);
        Button go=new Button(this);go.setText("确认并开始浏览任务");confirmOverlay.addView(go);
        Runnable update=()->{
            int kinds=0,times=0;
            for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked()){kinds++;times+=items.get(i).remaining;}
            totals.setText("已选 "+kinds+" 类浏览任务 / 剩余可做 "+times+" 次");
            int detected=requestLimit>0?Math.min(requestLimit,times):times;
            limitText.setText("自动检测：本轮可执行 "+detected+" 次（仅核对成功才计数）"+(requestLimit>0?"；受指令数量限制":""));go.setEnabled(times>0);
        };
        for(CheckBox check:checks)check.setOnCheckedChangeListener((v,c)->update.run());
        update.run();
        go.setOnClickListener(v->{
            go.setEnabled(false);
            BrowsePlan selected=new BrowsePlan();List<BrowsePlan.Item> chosen=new ArrayList<>();
            for(int i=0;i<checks.size();i++)if(checks.get(i).isChecked()){
                BrowsePlan.Item item=items.get(i);boolean force=slideChecks.get(i).isChecked();
                selected.add(new BrowsePlan.Item(item.id,item.row,force));chosen.add(item);
                getSharedPreferences("scroll-overrides",0).edit().putBoolean(learnKey(item.id),force).apply();
            }
            for(int i=0;i<checks.size();i++)if(!checks.get(i).isChecked())selected.exclude(items.get(i).id);
            autoDiscover=discover.isChecked();nextDiscoveryAt=0;
            int wanted=selected.executionLimit(requestLimit);closeConfirmation();
            // Let focus return to the target app before re-reading it.
            handler.postDelayed(()->{try{validateAndStart(selected,chosen,wanted);}catch(RuntimeException e){pause("确认检查异常："+e.getClass().getSimpleName()+"；未开始任务");}},450);
        });
        Button cancel=new Button(this);cancel.setText("取消，返回任务列表");cancel.setOnClickListener(v->{closeConfirmation();setStatus("已取消；可滚动任务列表后重新扫描");});confirmOverlay.addView(cancel);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(380)),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.CENTER;
        try{((WindowManager)getSystemService(WINDOW_SERVICE)).addView(confirmOverlay,p);setStatus("扫描完成："+items.size()+"类 · 耗时"+previewElapsed+"毫秒；请确认任务");}catch(RuntimeException e){confirmOverlay=null;setStatus("无法显示任务确认面板："+e.getClass().getSimpleName()+"；已停止");}
    }
    private void validateAndStart(BrowsePlan selected,List<BrowsePlan.Item> chosen,int wanted){
        AccessibilityNodeInfo root=applicationRoot();Map<String,BrowsePlan.Item> current=new HashMap<>();
        if(root==null){setStatus("页面未就绪，请重新扫描确认");return;}
        try{
            if(!target.equals(String.valueOf(root.getPackageName()))){setStatus("页面已切换，请返回任务列表重新扫描");return;}
            scanned=0;if(TaskRules.blockedScreen(flatten(root,0))){setStatus("检测到验证或登录，未开始");return;}
            for(Candidate c:scanCandidates(root,Collections.emptySet())){
                try{current.put(c.key,new BrowsePlan.Item(c.key,c.rowText));}finally{c.node.recycle();}
            }
        }finally{previewing=false;scanExcluded.clear();root.recycle();}
        for(BrowsePlan.Item item:chosen){BrowsePlan.Item now=current.get(item.id);if(now==null||now.remaining!=item.remaining||!now.signature.equals(item.signature)){setStatus("任务或进度已变化，请重新扫描确认");return;}}
        if(selected.total()<1){setStatus("未选择任务，未开始");return;}
        approved=selected;maxTasks=Math.min(wanted,selected.total());count=0;attempted.clear();deferredTask="";nextTaskAt=0;start();
    }
    private void start(){
        if(target.isEmpty())return;
        running=true;busy=false;unreadableSince=0;phase=0;since=SystemClock.elapsedRealtime();runSince=since;listScrolls=0;pendingScroll=false;seenLists.clear();toggle.setText("停止");handler.removeCallbacks(pulse);setStatus("识别浏览任务中…");handler.post(pulse);
    }
    private void setStatus(String s){status=s;if(message!=null)message.setText((target.equals("com.taobao.taobao")?"淘宝":"京东")+" · 完成 "+count+(maxTasks>0?"/"+maxTasks:"（数量待检测）")+" 次 · 跳过 "+approved.skippedCount()+" 类\n"+s);if(markScroll!=null)markScroll.setEnabled(running&&phase==1&&!needsScroll);}
    private void advance(long now,String reason){phase=0;since=now;nextTaskAt=now+350;deferredTask=taskId;pendingScroll=false;seenLists.clear();listScrolls=0;setStatus(reason+"；即将自动执行下一个已勾选浏览任务…");}
    private void pause(String s){running=false;handler.removeCallbacksAndMessages(null);busy=false;setStatus(s);if(toggle!=null)toggle.setText("扫描确认");}
    private void closeConfirmation(){if(confirmOverlay!=null){try{((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(confirmOverlay);}catch(RuntimeException ignored){}confirmOverlay=null;}}
    public void dismiss(){closeConfirmation();pause("已关闭");if(overlay!=null){try{((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(overlay);}catch(RuntimeException ignored){}overlay=null;}message=null;toggle=null;}
    private void tick(){
        if(!running||busy)return;
        AccessibilityNodeInfo root=applicationRoot();
        if(root==null){
            long now=SystemClock.elapsedRealtime();if(unreadableSince==0)unreadableSince=now;
            if(now-unreadableSince>10000)pause("连续10秒无法读取页面，请手动返回任务页");return;
        }
        unreadableSince=0;
        try{
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(!pkg.equals(target)){pause("已离开所选应用，操作已暂停");return;}
            scanned=0;String text=flatten(root,0);
            if(TaskRules.blockedScreen(text)){pause("检测到验证或敏感操作，请手动处理");return;}
            long now=SystemClock.elapsedRealtime();
            if(now-runSince>=maxMinutes*60000L){pause("已到指令时间上限，请手动核对当前任务");return;}
            if(phase==0){
                if((requestLimit>0&&count>=requestLimit)||(!autoDiscover&&count>=maxTasks)){
                    pause("已完成本轮执行数量，请手动检查奖励");return;
                }
                if(now<nextTaskAt)return;
                if(pendingScroll){
                    if(now-since<2500)return;
                    pendingScroll=false;
                    if(text.equals(lastList)||seenLists.contains(text)){pause("列表已到底或没有新任务；"+TaskRules.manualReason(text));return;}
                    since=now;
                }
                if(text.trim().length()<8){if(now-since>8000)pause("没有足够的可读文字，暂不支持此页面");return;}
                scanned=0;Candidate c=find(root,0);
                if(c==null&&!deferredTask.isEmpty()){String previous=deferredTask;deferredTask="";scanned=0;c=find(root,0);deferredTask=previous;}
                // Run a ready confirmed task first; scan for new tasks only when needed.
                if(c==null&&autoDiscover&&now>=nextDiscoveryAt){
                    nextDiscoveryAt=now+2500;
                    int added=discoverTasks(root);
                    if(added>0){
                        maxTasks=approved.executionLimit(requestLimit);
                        scanned=0;c=find(root,0);
                        if(c==null&&!deferredTask.isEmpty()){
                            String previous=deferredTask;deferredTask="";scanned=0;c=find(root,0);deferredTask=previous;
                        }
                    }
                }
                if(c==null){
                    if(now-since>6000){
                        scanned=0;
                        if(isTaskList(text)&&listScrolls<8&&scrollTaskList(root,0)){
                            lastList=text;seenLists.add(text);listScrolls++;pendingScroll=true;since=now;setStatus("查看下一屏任务（"+listScrolls+"/8）；跳过非浏览任务");return;
                        }
                        pause(autoDiscover?"当前已无可执行纯浏览任务（已完成、跳过或取消勾选），本轮结束":"已无已确认的浏览任务；可开启自动检测后续任务");
                    }return;
                }
                before=text;taskId=c.key;activeProgress=c.progress;expectedSeconds=c.seconds;attempted.add(c.attempt);taskSince=now;phase=1;leftTaskList=false;reopened=false;pendingEntry="";browseSwipes=0;backAttempts=0;lastSwipe=0;taskButton=new Rect(c.rect);clickFallbackUsed=false;timedOut=false;
                timing=new BrowseTiming();needsScroll=approved.forceScroll(taskId)||getSharedPreferences("scroll-overrides",0).getBoolean(learnKey(taskId),false)||BrowseTiming.requiresScroll(c.rowText);
                boolean clicked=c.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);c.node.recycle();
                if(!clicked){clicked=tap(taskButton);clickFallbackUsed=true;}
                if(!clicked){approved.skip(taskId);advance(now,"本项按钮无法点击，已跳过");return;}
                setStatus("已打开浏览任务，等待页面反馈…");
            }else if(phase==1){
                long elapsed=now-taskSince;
                // Apps may return automatically or the user may press BACK before our timer.
                // A known row is stronger evidence than generic headings/button counts.
                if(leftTaskList&&returnedToList(root,text)){
                    phase=2;returnSince=now;setStatus("已返回已确认任务页，核对进度后自动继续…");return;
                }
                if(elapsed>90000){
                    if(isTaskList(text)){approved.skip(taskId);advance(now,"本项超时且已在列表，已跳过");return;}
                    if(performGlobalAction(GLOBAL_ACTION_BACK)){timedOut=true;phase=2;returnSince=now;backAttempts=1;setStatus("本项超时，返回列表后核对并继续下一项…");return;}
                    pause("本项超时且无法返回，已停止");return;
                }
                if(!leftTaskList&&elapsed>1500&&!isTaskList(text)&&!text.equals(before)){leftTaskList=true;browseSince=now;}
                if(leftTaskList){timing.observe(text,now);needsScroll=needsScroll||BrowseTiming.requiresScroll(text);if(!needsScroll){scanned=0;needsScroll=scanScrollPrompts(root,0);}}
                if(!leftTaskList&&elapsed>=4000&&!clickFallbackUsed&&text.equals(before)){
                    clickFallbackUsed=true;
                    if(tap(taskButton)){setStatus("网页按钮未跳转，尝试点击已识别的按钮位置…");return;}
                }
                if(!leftTaskList&&elapsed>12000){pause("按钮点击后未确认进入任务；请检查是否有遮挡或页面文字不可读");return;}
                // A pre-existing success label in the task list must not count as success.
                boolean successHint=elapsed>=3000&&!text.equals(before)&&TaskRules.newCompletion(text,before);
                boolean timedCheck=leftTaskList&&timing.ready(expectedSeconds,now-browseSince,now)&&(!needsScroll||browseSwipes>0);
                if(successHint||timedCheck){
                    // Some task pages update the list in place; do not close it with BACK.
                    if(!returnedToList(root,text)&&!performGlobalAction(GLOBAL_ACTION_BACK)){pause("返回失败，请手动返回任务列表");return;}
                    phase=2;returnSince=now;backAttempts=1;setStatus(successHint?"检测到完成提示，返回核对进度…":"浏览计时结束，返回核对是否增加进度…");return;
                }
                if(BrowseScroll.due(leftTaskList,needsScroll,now-browseSince,now-lastSwipe)){
                    scanned=0;
                    if(swipeBrowse(root)){
                        browseSwipes++;lastSwipe=now;setStatus("持续缓慢滑动 · 第 "+browseSwipes+" 次；等待完成反馈…");return;
                    }
                    lastSwipe=now;
                }
                int seconds=BrowseTiming.remaining(text);
                setStatus(seconds>=0?"页面倒计时 "+seconds+" 秒"+(needsScroll?" · 按提示滑动":" · 停留浏览"):"浏览中 "+(leftTaskList?(now-browseSince)/1000:0)+" 秒"+(needsScroll?" · 需要滑动":" · 未要求滑动")+"；等待完成反馈");
            }else if(phase==2){
                if(now-returnSince<500)return;
                if(!returnedToList(root,text)){
                    if(!reopened){
                        scanned=0;String entry=findEntry(root,0);
                        if(!entry.isEmpty()){
                            if(!entry.equals(pendingEntry)){pendingEntry=entry;setStatus("已返回活动首页，准备打开任务列表…");return;}
                            scanned=0;if(clickEntry(root,entry,0)){reopened=true;returnSince=now;setStatus("已打开任务列表，核对进度中…");return;}
                        }
                    }
                    if(!reopened&&pendingEntry.isEmpty()&&now-returnSince>=5000&&backAttempts<3&&TaskRules.seconds(text)>=0){
                        if(performGlobalAction(GLOBAL_ACTION_BACK)){backAttempts++;returnSince=now;setStatus("仍在浏览页，继续返回（"+backAttempts+"/3）…");return;}
                    }
                    if(now-returnSince>12000)pause("未确认返回任务列表，请手动返回后重新开始");return;
                }
                confirmVisited=0;verifiedProgress=null;
                if(confirmedRow(root,taskId,0)){
                    if(activeProgress==null)approved.confirmOne(taskId);
                    else if(!approved.confirmProgress(taskId,verifiedProgress)){pause("任务进度变化异常，请重新扫描确认");return;}
                    getSharedPreferences("learned",0).edit().putBoolean(learnKey(taskId),true).apply();
                    count++;advance(now,"已核对完成并记住任务");
                }else if(timedOut||now-returnSince>8000){approved.skip(taskId);advance(now,"已回到列表，但本项完成未确认，跳过且不计成功");}
            }
        }finally{root.recycle();}
    }
    /** One bounded traversal collects all candidates instead of restarting at the root per row. */
    private List<Candidate> scanCandidates(AccessibilityNodeInfo root,Set<String> excluded){
        List<Candidate> result=new ArrayList<>();collecting=result;previewing=true;
        scanExcluded.clear();scanExcluded.addAll(excluded);scanned=0;
        try{find(root,0);return result;}
        catch(RuntimeException e){for(Candidate c:result)c.node.recycle();throw e;}
        finally{collecting=null;previewing=false;scanExcluded.clear();}
    }
    private int discoverTasks(AccessibilityNodeInfo root){
        int added=0;
        for(Candidate c:scanCandidates(root,approved.knownIds())){
            try{if(approved.addDiscovered(new BrowsePlan.Item(c.key,c.rowText),autoDiscover))added++;}
            finally{c.node.recycle();}
        }
        return added;
    }
    private boolean returnedToList(AccessibilityNodeInfo root,String text){
        if(isTaskList(text))return true;
        confirmVisited=0;
        return containsApprovedRow(root,0);
    }
    private boolean containsApprovedRow(AccessibilityNodeInfo n,int depth){
        if(depth>45||++confirmVisited>2400)return false;
        String t=n.isVisibleToUser()?TaskRows.normalize(label(n)):"";
        if(!TaskRows.action(t).isEmpty()||TaskRules.rowCompleted(t)||TaskProgress.containsCounter(t)){
            AccessibilityNodeInfo row=AccessibilityNodeInfo.obtain(n);
            for(int k=0;k<5&&row!=null;k++){
                scanned=0;String text=flatten(row,0);TaskProgress p=TaskProgress.parse(text);
                String id=p==null?key(text):p.identity();
                boolean known=text.length()<220&&approved.recognizes(id,text)
                    &&(p!=null||TaskRules.rowCompleted(text)||TaskRows.merged(text));
                AccessibilityNodeInfo parent=row.getParent();row.recycle();row=parent;
                if(known){if(row!=null)row.recycle();return true;}
            }
            if(row!=null)row.recycle();
        }
        for(int i=0;i<n.getChildCount();i++){
            AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean known=containsApprovedRow(c,depth+1);c.recycle();if(known)return true;}
        }
        return false;
    }
    private boolean isTaskList(String text){return NavigationRules.isTaskList(text);}
    private String flatten(AccessibilityNodeInfo n,int depth){return TaskRows.normalize(flattenRaw(n,depth));}
    private String flattenRaw(AccessibilityNodeInfo n,int depth){
        if(n==null||depth>45||++scanned>2400)return "";
        StringBuilder s=new StringBuilder();CharSequence t=n.isVisibleToUser()?n.getText():null,d=n.isVisibleToUser()?n.getContentDescription():null;if(t!=null)s.append(t).append(' ');if(d!=null&&(t==null||!d.toString().equals(t.toString())))s.append(d).append(' ');
        if(TaskRows.merged(s.toString()))return TaskRows.normalize(s.toString());
        for(int i=0;i<n.getChildCount()&&s.length()<18000;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){s.append(flattenRaw(c,depth+1));c.recycle();}}return s.toString();
    }
    private String label(AccessibilityNodeInfo n){CharSequence t=n.getText(),d=n.getContentDescription();String text=t==null?"":t.toString().trim(),desc=d==null?"":d.toString().trim();if(text.isEmpty())return desc;if(desc.isEmpty()||text.contains(desc))return text;if(desc.contains(text))return desc;return text+" "+desc;}
    private String key(String row){return row.replaceAll("去浏览|去完成|逛一逛|去逛逛|任务已完成|已完成|已领取|已达成|已浏览|明日再来|已打卡|完成|[0-9]+|\\s+","");}
    private Candidate find(AccessibilityNodeInfo n,int depth){
        if(depth>45||++scanned>2400||(collecting!=null&&collecting.size()>=50))return null;
        String nodeLabel=n.isVisibleToUser()?label(n):"",button=TaskRows.action(nodeLabel);
        // Some buttons expose 去 / 完 / 成 only in separate child nodes.
        if(button.isEmpty()&&n.isVisibleToUser()&&n.getChildCount()>0){
            Rect bounds=new Rect();n.getBoundsInScreen(bounds);
            if(bounds.width()<=dp(260)&&bounds.height()<=dp(100)){
                int saved=scanned;scanned=0;String combined=flatten(n,0);scanned=saved;
                if(!TaskRows.action(combined).isEmpty()){nodeLabel=combined;button=TaskRows.action(combined);}
            }
        }
        boolean merged=TaskRows.merged(nodeLabel);
        if(!button.isEmpty()){
            AccessibilityNodeInfo row=merged?AccessibilityNodeInfo.obtain(n):n.getParent();
            for(int k=0;k<5&&row!=null;k++){
                int saved=scanned;scanned=0;String text=flatten(row,0);scanned=saved;
                // Do not treat an entire list containing multiple buttons as one task row.
                int buttons=TaskRows.actionCount(text);
                TaskProgress progress=TaskProgress.parse(text);
                String id=progress==null?key(text):progress.identity();
                String attempt=progress==null?id:progress.attemptKey();
                if(buttons==1&&TaskRules.isBrowseTask(text,button)&&(previewing?!scanExcluded.contains(id):!id.equals(deferredTask)&&approved.allows(id,text)&&!attempted.contains(attempt))&&(!learnedOnly||getSharedPreferences("learned",0).getBoolean(learnKey(id),false))){
                    AccessibilityNodeInfo clickable=AccessibilityNodeInfo.obtain(n);
                    for(int p=0;p<3&&!clickable.isClickable();p++){AccessibilityNodeInfo parent=clickable.getParent();if(parent==null)break;clickable.recycle();clickable=parent;}
                    Rect actionBounds=new Rect();clickable.getBoundsInScreen(actionBounds);
                    if(actionBounds.height()>dp(230)){clickable.recycle();clickable=AccessibilityNodeInfo.obtain(n);}
                    Rect rect=new Rect();n.getBoundsInScreen(rect);
                    if(merged&&rect.width()>dp(120)){rect.left=rect.right-Math.min(dp(86),rect.width()/3);}
                    if(!rect.isEmpty()&&rect.width()>=dp(20)&&rect.width()<dp(260)&&rect.height()>=dp(12)&&rect.height()<dp(180)&&(previewing||!overlapsOverlay(rect))){
                        Candidate candidate=new Candidate(clickable,id,attempt,progress,TaskRules.seconds(text),rect,text);
                        row.recycle();row=null;
                        if(collecting==null)return candidate;
                        collecting.add(candidate);scanExcluded.add(id);break;
                    }
                    clickable.recycle();
                }
                AccessibilityNodeInfo parent=row.getParent();row.recycle();row=parent;
            }
            if(row!=null)row.recycle();
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){Candidate found=find(c,depth+1);c.recycle();if(found!=null)return found;}}
        return null;
    }
    private boolean confirmedRow(AccessibilityNodeInfo n,String id,int depth){
        if(depth>45||++confirmVisited>2400)return false;
        String t=n.isVisibleToUser()?TaskRows.normalize(label(n)):"";
        // WebViews often put the title and (1/10) in separate sibling nodes.
        if((activeProgress!=null&&(t.contains(activeProgress.name)||TaskProgress.containsCounter(t)))||TaskRules.rowCompleted(t)){
            AccessibilityNodeInfo row=AccessibilityNodeInfo.obtain(n);
            for(int k=0;k<5&&row!=null;k++){
                scanned=0;String text=flatten(row,0);TaskProgress after=activeProgress==null?TaskProgress.parse(text):activeProgress.confirmedAfter(text);
                boolean match=text.length()<220&&(activeProgress!=null?after!=null&&after.advancedFrom(activeProgress):key(text).equals(id)&&TaskRules.rowCompleted(text));
                AccessibilityNodeInfo parent=row.getParent();row.recycle();row=parent;if(match){verifiedProgress=after;if(row!=null)row.recycle();return true;}
            }
            if(row!=null)row.recycle();
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=confirmedRow(c,id,depth+1);c.recycle();if(ok)return true;}}return false;
    }
    private boolean overlapsOverlay(Rect r){if(overlay==null)return false;int[] xy=new int[2];overlay.getLocationOnScreen(xy);return Rect.intersects(r,new Rect(xy[0],xy[1],xy[0]+overlay.getWidth(),xy[1]+overlay.getHeight()));}
    // Task-list entry labels only; no reward claims or purchase controls.
    private String findEntry(AccessibilityNodeInfo n,int depth){
        if(depth>45||++scanned>2400)return "";
        String t=n.isVisibleToUser()?TaskRows.normalize(label(n)):"";if(NavigationRules.isEntry(t))return t;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){String found=findEntry(c,depth+1);c.recycle();if(!found.isEmpty())return found;}}return "";
    }
    private boolean clickEntry(AccessibilityNodeInfo n,String entry,int depth){
        if(depth>45||++scanned>2400)return false;
        if(n.isVisibleToUser()&&TaskRows.normalize(label(n)).equals(entry)){
            AccessibilityNodeInfo click=AccessibilityNodeInfo.obtain(n);
            for(int k=0;k<2&&!click.isClickable();k++){AccessibilityNodeInfo p=click.getParent();if(p==null)break;click.recycle();click=p;}
            Rect r=new Rect();n.getBoundsInScreen(r);boolean ok=false;if(!r.isEmpty()&&r.height()<dp(200)&&!overlapsOverlay(r)){ok=click.isEnabled()&&click.performAction(AccessibilityNodeInfo.ACTION_CLICK);if(!ok)ok=tap(r);}click.recycle();return ok;
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=clickEntry(c,entry,depth+1);c.recycle();if(ok)return true;}}return false;
    }
    private int occurrences(String s,String sub){int count=0,at=0;while((at=s.indexOf(sub,at))>=0){count++;at+=sub.length();}return count;}
    private boolean scrollTaskList(AccessibilityNodeInfo n,int depth){
        if(depth>45||++scanned>2400)return false;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=scrollTaskList(c,depth+1);c.recycle();if(ok)return true;}}
        if(n.isVisibleToUser()&&n.isScrollable()){
            int saved=scanned;scanned=0;String t=flatten(n,0);scanned=saved;
            if(t.contains("去完成")||t.contains("去浏览")||t.contains("已完成"))return n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }return false;
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private AccessibilityNodeInfo applicationRoot(){
        AccessibilityNodeInfo active=getRootInActiveWindow();scanWindows=0;
        // Never select a background JD/Taobao window while another app is foreground.
        if(active!=null){String p=String.valueOf(active.getPackageName());
            if(!p.equals(getPackageName())&&!p.equals(target))return active;
        }
        AccessibilityNodeInfo result=null;int best=-1,bestLayer=Integer.MIN_VALUE;
        for(AccessibilityWindowInfo w:getWindows()){
            if(w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){
                AccessibilityNodeInfo r=w.getRoot();
                if(r!=null){
                    if(target.equals(String.valueOf(r.getPackageName()))){
                        scanWindows++;int saved=scanned;scanned=0;String text=flatten(r,0);scanned=saved;
                        int score=TaskRows.actionCount(text)*10+(text.contains("浏览")?1:0);
                        if(score>best||(score==best&&w.getLayer()>bestLayer)){
                            if(result!=null)result.recycle();result=r;best=score;bestLayer=w.getLayer();
                        }else r.recycle();
                    }else r.recycle();
                }
            }
            w.recycle();
        }
        if(result!=null){if(active!=null)active.recycle();return result;}
        return active;
    }
    private boolean tap(Rect r){
        if(r==null||r.isEmpty()||overlapsOverlay(r))return false;
        Path p=new Path();p.moveTo(r.centerX(),r.centerY());return gesture(p,90);
    }
    private boolean gesture(Path p,int duration){
        if(!running||busy)return false;
        busy=true;
        boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,duration)).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription g){
                busy=false;
                if(duration>=BrowseScroll.DURATION_MS&&running){handler.removeCallbacks(pulse);handler.postDelayed(pulse,BrowseScroll.GAP_MS);}
            }
            @Override public void onCancelled(GestureDescription g){busy=false;}
        },handler);
        if(!accepted)busy=false;return accepted;
    }
    private boolean scanScrollPrompts(AccessibilityNodeInfo n,int depth){
        if(depth>60||++scanned>4000)return false;
        if(n.isVisibleToUser()&&BrowseTiming.requiresScroll(label(n)))return true;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean found=scanScrollPrompts(c,depth+1);c.recycle();if(found)return true;}}return false;
    }
    private boolean swipeBrowse(AccessibilityNodeInfo root){
        Rect r=new Rect();root.getBoundsInScreen(r);
        if(r.height()<dp(260)||r.width()<dp(200))return false;
        for(int fraction:new int[]{33,67,50}){
            int x=r.left+r.width()*fraction/100;
            Rect pathBounds=new Rect(x-2,r.top+r.height()*40/100,x+2,r.top+r.height()*68/100);
            if(overlapsOverlay(pathBounds))continue;
            Path p=new Path();boolean up=BrowseScroll.upward(browseSwipes);
            p.moveTo(x,up?pathBounds.bottom:pathBounds.top);p.lineTo(x,up?pathBounds.top:pathBounds.bottom);
            return gesture(p,BrowseScroll.DURATION_MS);
        }
        return false;
    }
    private String learnKey(String id){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((target+":"+version+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class Candidate{final AccessibilityNodeInfo node;final String key,attempt,rowText;final TaskProgress progress;final int seconds;final Rect rect;Candidate(AccessibilityNodeInfo n,String k,String a,TaskProgress p,int s,Rect r,String row){node=n;key=k;attempt=a;progress=p;seconds=s;rect=new Rect(r);rowText=row;}}
}
