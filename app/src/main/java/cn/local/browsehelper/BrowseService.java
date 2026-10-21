package cn.local.browsehelper;

import android.accessibilityservice.AccessibilityService;
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
import android.widget.*;
import java.util.*;

public class BrowseService extends AccessibilityService {
    public static BrowseService instance;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String target="", status="未开始", before="", taskId="";
    private boolean running, busy;
    private boolean leftTaskList, reopened;
    private TaskProgress activeProgress;
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
    private int maxTasks=20, maxMinutes=20;
    private boolean learnedOnly;
    private String version="";
    private LinearLayout overlay;
    private TextView message;
    private Button toggle;
    private final Set<String> attempted=new HashSet<>();
    private final Runnable pulse=new Runnable(){public void run(){try{tick();}catch(RuntimeException e){pause("页面读取异常，请重新打开任务页");}if(running)handler.postDelayed(this,1000);}};
    @Override protected void onServiceConnected(){super.onServiceConnected();instance=this;}
    public String statusText(){return status;}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){}
    @Override public void onInterrupt(){pause("服务中断，请重新检查页面");}
    @Override public void onDestroy(){dismiss();instance=null;super.onDestroy();}
    @Override public boolean onUnbind(Intent i){dismiss();instance=null;return super.onUnbind(i);}
    public boolean prepare(String pkg){
        dismiss();target=pkg;attempted.clear();count=0;maxTasks=20;maxMinutes=20;learnedOnly=false;
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(pkg,0);version=String.valueOf(android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode);}catch(Exception e){return false;}
        overlay=new LinearLayout(this);overlay.setOrientation(1);overlay.setPadding(dp(12),dp(8),dp(12),dp(8));overlay.setBackgroundColor(Color.rgb(20,39,51));
        message=new TextView(this);message.setTextColor(Color.WHITE);message.setTextSize(12);overlay.addView(message,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(this);
        toggle=new Button(this);toggle.setTextSize(12);toggle.setText("开始");toggle.setOnClickListener(v->{if(running)pause("已停止，请确认任务列表后再开始");else start();});actions.addView(toggle,new LinearLayout.LayoutParams(0,dp(44),1));
        Button close=new Button(this);close.setText("关闭");close.setTextSize(12);close.setOnClickListener(v->dismiss());actions.addView(close,new LinearLayout.LayoutParams(0,dp(44),1));overlay.addView(actions);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(232),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.END;p.y=dp(100);p.x=dp(8);
        message.setOnTouchListener(new android.view.View.OnTouchListener(){float downX,downY;int oldX,oldY;public boolean onTouch(android.view.View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();oldX=p.x;oldY=p.y;return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){p.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-dp(232),oldX-(int)(e.getRawX()-downX)));p.y=Math.max(dp(24),Math.min(getResources().getDisplayMetrics().heightPixels-dp(150),oldY+(int)(e.getRawY()-downY)));try{((WindowManager)getSystemService(WINDOW_SERVICE)).updateViewLayout(overlay,p);}catch(RuntimeException ignored){}return true;}return true;}});
        try{((WindowManager)getSystemService(WINDOW_SERVICE)).addView(overlay,p);}catch(RuntimeException e){overlay=null;return false;}
        setStatus("先手动打开活动任务列表，再点开始");return true;
    }
    public void configure(LocalCommand c){maxTasks=c.maxTasks;maxMinutes=c.minutes;learnedOnly=c.learnedOnly;setStatus("手动打开任务列表后开始 · 最多"+maxTasks+"项 / "+maxMinutes+"分钟"+(learnedOnly?" · 仅已学任务":""));}
    private void start(){
        if(target.isEmpty())return;
        running=true;busy=false;phase=0;since=SystemClock.elapsedRealtime();runSince=since;listScrolls=0;pendingScroll=false;seenLists.clear();toggle.setText("停止");handler.removeCallbacks(pulse);setStatus("识别浏览任务中…");handler.post(pulse);
    }
    private void setStatus(String s){status=s;if(message!=null)message.setText((target.equals("com.taobao.taobao")?"淘宝":"京东")+" · 本轮确认 "+count+" 项\n"+s);}
    private void pause(String s){running=false;handler.removeCallbacksAndMessages(null);busy=false;setStatus(s);if(toggle!=null)toggle.setText("开始");}
    public void dismiss(){pause("已关闭");if(overlay!=null){try{((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(overlay);}catch(RuntimeException ignored){}overlay=null;}message=null;toggle=null;}
    private void tick(){
        if(!running||busy)return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null){if(SystemClock.elapsedRealtime()-since>10000)pause("无法读取页面，请手动返回任务页");return;}
        try{
            String pkg=root.getPackageName()==null?"":root.getPackageName().toString();
            if(!pkg.equals(target)){pause("已离开所选应用，操作已暂停");return;}
            scanned=0;String text=flatten(root,0);
            if(TaskRules.blockedScreen(text)){pause("检测到验证或敏感操作，请手动处理");return;}
            long now=SystemClock.elapsedRealtime();
            if(now-runSince>=maxMinutes*60000L){pause("已到指令时间上限，请手动核对当前任务");return;}
            if(phase==0){
                if(count>=maxTasks){pause("本轮已达到"+maxTasks+"项上限，请手动检查奖励");return;}
                if(pendingScroll){
                    if(now-since<2500)return;
                    pendingScroll=false;
                    if(text.equals(lastList)||seenLists.contains(text)){pause("列表已到底或没有新任务；"+TaskRules.manualReason(text));return;}
                    since=now;
                }
                if(text.trim().length()<8){if(now-since>8000)pause("没有足够的可读文字，暂不支持此页面");return;}
                scanned=0;Candidate c=find(root,0);
                if(c==null){
                    if(now-since>6000){
                        scanned=0;
                        if(isTaskList(text)&&listScrolls<8&&scrollTaskList(root,0)){
                            lastList=text;seenLists.add(text);listScrolls++;pendingScroll=true;since=now;setStatus("查看下一屏任务（"+listScrolls+"/8）；跳过非浏览任务");return;
                        }
                        pause("没有更多可自动处理的浏览任务；"+TaskRules.manualReason(text));
                    }return;
                }
                before=text;taskId=c.key;activeProgress=c.progress;expectedSeconds=c.seconds;attempted.add(c.attempt);taskSince=now;phase=1;leftTaskList=false;reopened=false;pendingEntry="";
                boolean clicked=c.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);c.node.recycle();
                if(!clicked){pause("任务按钮不可点击，请手动处理");return;}
                setStatus("已打开浏览任务，等待页面反馈…");
            }else if(phase==1){
                long elapsed=now-taskSince;
                if(elapsed>90000){pause("90秒内未确认任务完成，请手动检查");return;}
                if(!leftTaskList&&elapsed>1500&&!isTaskList(text)&&!text.equals(before)){leftTaskList=true;browseSince=now;}
                // A pre-existing success label in the task list must not count as success.
                boolean successHint=elapsed>=3000&&!text.equals(before)&&TaskRules.newCompletion(text,before);
                boolean timedCheck=TaskRules.readyToVerify(expectedSeconds,now-browseSince,leftTaskList);
                if(successHint||timedCheck){
                    // Some task pages update the list in place; do not close it with BACK.
                    if(!isTaskList(text)&&!performGlobalAction(GLOBAL_ACTION_BACK)){pause("返回失败，请手动返回任务列表");return;}
                    phase=2;returnSince=now;setStatus(successHint?"检测到完成提示，返回核对进度…":"浏览计时结束，返回核对是否增加进度…");return;
                }
                int seconds=TaskRules.seconds(text);
                setStatus(seconds>=0?"页面倒计时约 "+seconds+" 秒，等待完成提示":"浏览中 "+elapsed/1000+" 秒；等待明确的完成提示");
            }else if(phase==2){
                if(now-returnSince<2000)return;
                if(!isTaskList(text)){
                    if(!reopened&&target.equals("com.taobao.taobao")){
                        scanned=0;String entry=findEntry(root,0);
                        if(!entry.isEmpty()){
                            if(!entry.equals(pendingEntry)){pendingEntry=entry;setStatus("已返回活动首页，准备打开任务列表…");return;}
                            scanned=0;if(clickEntry(root,entry,0)){reopened=true;returnSince=now;setStatus("已打开任务列表，核对进度中…");return;}
                        }
                    }
                    if(now-returnSince>12000)pause("未确认返回任务列表，请手动返回后重新开始");return;
                }
                confirmVisited=0;
                if(confirmedRow(root,taskId,0)){
                    getSharedPreferences("learned",0).edit().putBoolean(learnKey(taskId),true).apply();
                    count++;phase=0;since=now;setStatus("已核对完成并记住任务，继续下一项…");
                }else if(now-returnSince>8000)pause("返回后未确认该任务已完成，请手动核对");
            }
        }finally{root.recycle();}
    }
    private boolean isTaskList(String text){return (text.contains("做任务赚能量")||text.contains("任务")||text.contains("赚步数"))&&(text.contains("去浏览")||text.contains("去完成")||text.contains("已完成")||text.contains("明日再来"));}
    private String flatten(AccessibilityNodeInfo n,int depth){
        if(n==null||depth>22||++scanned>1600||!n.isVisibleToUser())return "";
        StringBuilder s=new StringBuilder();CharSequence t=n.getText(),d=n.getContentDescription();if(t!=null)s.append(t).append(' ');if(d!=null&&(t==null||!d.toString().equals(t.toString())))s.append(d).append(' ');
        for(int i=0;i<n.getChildCount()&&s.length()<18000;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){s.append(flatten(c,depth+1));c.recycle();}}return s.toString();
    }
    private String label(AccessibilityNodeInfo n){CharSequence t=n.getText();if(t==null||t.length()==0)t=n.getContentDescription();return t==null?"":t.toString().trim();}
    private String key(String row){return row.replaceAll("去浏览|去完成|逛一逛|去逛逛|任务已完成|已完成|已领取|已达成|已浏览|明日再来|已打卡|完成|[0-9]+|\\s+","");}
    private Candidate find(AccessibilityNodeInfo n,int depth){
        if(depth>22||++scanned>1600||!n.isVisibleToUser())return null;
        String button=label(n);
        if(button.equals("去浏览")||button.equals("去完成")||button.equals("逛一逛")||button.equals("去逛逛")){
            AccessibilityNodeInfo row=n.getParent();
            for(int k=0;k<5&&row!=null;k++){
                int saved=scanned;scanned=0;String text=flatten(row,0);scanned=saved;
                // Do not treat an entire list containing multiple buttons as one task row.
                int buttons=occurrences(text,"去浏览")+occurrences(text,"去完成")+occurrences(text,"逛一逛")+occurrences(text,"去逛逛");
                TaskProgress progress=TaskProgress.parse(text);
                String id=progress==null?key(text):progress.identity();
                String attempt=progress==null?id:progress.attemptKey();
                if(buttons==1&&TaskRules.isBrowseTask(text,button)&&!attempted.contains(attempt)&&(!learnedOnly||getSharedPreferences("learned",0).getBoolean(learnKey(id),false))){
                    AccessibilityNodeInfo clickable=AccessibilityNodeInfo.obtain(n);
                    for(int p=0;p<3&&!clickable.isClickable();p++){AccessibilityNodeInfo parent=clickable.getParent();if(parent==null)break;clickable.recycle();clickable=parent;}
                    Rect rect=new Rect();clickable.getBoundsInScreen(rect);
                    if(clickable.isClickable()&&clickable.isEnabled()&&!rect.isEmpty()&&rect.height()<dp(230)&&!overlapsOverlay(rect)){row.recycle();return new Candidate(clickable,id,attempt,progress,TaskRules.seconds(text));}
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
        if(depth>20||++confirmVisited>1600||!n.isVisibleToUser())return false;
        String t=label(n);
        if((activeProgress!=null&&t.replaceAll("\\s+","").contains(activeProgress.name))||TaskRules.rowCompleted(t)){
            AccessibilityNodeInfo row=n.getParent();
            for(int k=0;k<4&&row!=null;k++){
                scanned=0;String text=flatten(row,0);TaskProgress after=TaskProgress.parse(text);
                boolean match=text.length()<220&&(activeProgress!=null?after!=null&&after.advancedFrom(activeProgress):key(text).equals(id)&&TaskRules.rowCompleted(text));
                AccessibilityNodeInfo parent=row.getParent();row.recycle();row=parent;if(match){if(row!=null)row.recycle();return true;}
            }
            if(row!=null)row.recycle();
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=confirmedRow(c,id,depth+1);c.recycle();if(ok)return true;}}return false;
    }
    private boolean overlapsOverlay(Rect r){if(overlay==null)return false;int[] xy=new int[2];overlay.getLocationOnScreen(xy);return Rect.intersects(r,new Rect(xy[0],xy[1],xy[0]+overlay.getWidth(),xy[1]+overlay.getHeight()));}
    // Only two exact task-list entries seen in the user's activity screenshots.
    private String findEntry(AccessibilityNodeInfo n,int depth){
        if(depth>20||++scanned>1200||!n.isVisibleToUser())return "";
        String t=label(n);if(t.equals("去赚能量")||t.equals("赚步数"))return t;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){String found=findEntry(c,depth+1);c.recycle();if(!found.isEmpty())return found;}}return "";
    }
    private boolean clickEntry(AccessibilityNodeInfo n,String entry,int depth){
        if(depth>20||++scanned>1200||!n.isVisibleToUser())return false;
        if(label(n).equals(entry)){
            AccessibilityNodeInfo click=AccessibilityNodeInfo.obtain(n);
            for(int k=0;k<2&&!click.isClickable();k++){AccessibilityNodeInfo p=click.getParent();if(p==null)break;click.recycle();click=p;}
            Rect r=new Rect();click.getBoundsInScreen(r);boolean ok=click.isClickable()&&click.isEnabled()&&!r.isEmpty()&&r.height()<dp(200)&&!overlapsOverlay(r)&&click.performAction(AccessibilityNodeInfo.ACTION_CLICK);click.recycle();return ok;
        }
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=clickEntry(c,entry,depth+1);c.recycle();if(ok)return true;}}return false;
    }
    private int occurrences(String s,String sub){int count=0,at=0;while((at=s.indexOf(sub,at))>=0){count++;at+=sub.length();}return count;}
    private boolean scrollTaskList(AccessibilityNodeInfo n,int depth){
        if(depth>20||++scanned>1200||!n.isVisibleToUser())return false;
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){boolean ok=scrollTaskList(c,depth+1);c.recycle();if(ok)return true;}}
        if(n.isScrollable()){
            int saved=scanned;scanned=0;String t=flatten(n,0);scanned=saved;
            if(t.contains("去完成")||t.contains("去浏览")||t.contains("已完成"))return n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        }return false;
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private String learnKey(String id){try{byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((target+":"+version+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final class Candidate{final AccessibilityNodeInfo node;final String key,attempt;final TaskProgress progress;final int seconds;Candidate(AccessibilityNodeInfo n,String k,String a,TaskProgress p,int s){node=n;key=k;attempt=a;progress=p;seconds=s;}}
}
