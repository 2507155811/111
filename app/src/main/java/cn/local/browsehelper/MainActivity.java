package cn.local.browsehelper;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private final int ink=Color.rgb(228,239,244), muted=Color.rgb(160,181,196), accent=Color.rgb(68,220,185);
    private TextView status;
    private boolean consent;
    private LinearLayout body;
    private EditText command;
    private TextView learned;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12,22,33));
        getWindow().setNavigationBarColor(Color.rgb(12,22,33));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(12,22,33));
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        body=new LinearLayout(this); body.setOrientation(1); body.setPadding(dp(24),dp(20),dp(24),dp(32));scroll.addView(body);setContentView(scroll);
        text("本地运行  /  ANDROID",12,accent,8);
        text("浏览小助手",32,ink,10);
        text("淘宝 · 京东浏览任务辅助",16,muted,26);
        status=text("检查服务状态…",14,accent,22);
        text("本地指令助手",19,ink,8);
        text("离线规则式助手，不是大模型。支持：淘宝浏览3个任务、京东浏览5分钟、淘宝只做已学任务、停止。指令不会绕过下方授权。",14,muted,8);
        command=new EditText(this);command.setTextColor(ink);command.setHintTextColor(muted);command.setTextSize(14);command.setHint("例如：京东浏览3个任务");command.setSingleLine(true);body.addView(command,new LinearLayout.LayoutParams(-1,dp(56)));
        button("执行指令",()->executeCommand(),true);
        learned=text("",13,accent,12);
        text("01  开启无障碍服务",19,ink,8);
        text("在系统无障碍设置中找到“浏览小助手”，阅读授权说明后开启。小米的设置入口可能因 HyperOS 版本而不同。",14,muted,12);
        button("打开无障碍设置",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),false);
        text("02  选择平台，打开活动任务页",19,ink,8);
        text("进入应用后，手动打开任务列表，点悬浮条“扫描确认”。核对当前可见的浏览任务种类、剩余次数，勾选任务，数量按所选任务剩余次数自动计算，确认后开始。只做已勾选的明确浏览任务；默认返回后自动检测后续纯浏览任务，可在确认面板关闭；取消勾选的条目不会自动加入。拖动悬浮条文字可移动。",14,muted,10);
        CheckBox check=new CheckBox(this);check.setText("我理解：将读取当前页面文字，并自动点击浏览任务；识别不明时暂停。可能触发平台风控，奖励不保证到账。");check.setTextColor(muted);check.setTextSize(14);check.setPadding(0,dp(6),0,dp(14));check.setOnCheckedChangeListener((v,c)->consent=c);body.addView(check);
        button("打开淘宝并显示控制条",()->open("com.taobao.taobao"),true);
        button("打开京东并显示控制条",()->open("com.jingdong.app.mall"),true);
        button("清除学习记录",()->new android.app.AlertDialog.Builder(this).setMessage("清除已确认的任务特征？不会影响淘宝、京东账户。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->clearLearned()).show(),false);
        text("03  随时停止，按结果确认",19,ink,8);
        text("悬浮条可随时停止。遇到验证码、登录、支付或陌生界面会暂停；无需上传截图，不使用网络、不保存页面内容。暂不支持纯图片 / 无可读文字的任务页。",14,muted,14);
        button("停止并关闭控制条",()->{if(BrowseService.instance!=null)BrowseService.instance.dismiss();refresh();},false);
        text("适配说明",17,ink,8);
        text("面向小米 17 Pro Max / HyperOS，安卓 8.0 以上。没有手机实测，尚不能保证兼容当前双11活动。若服务被系统停止，可检查应用电池设置和后台运行权限；不要关闭系统安全保护。",13,muted,20);
        text("版本 0.17.0 · 同步活动次数与恢复暂缓任务",12,muted,0);
    }
    @Override public void onResume(){super.onResume();refresh();}
    private void refresh(){if(status!=null)status.setText(BrowseService.instance==null?"● 无障碍服务尚未连接":"● 服务已连接 · "+BrowseService.instance.statusText());if(learned!=null)learned.setText("已学会 "+getSharedPreferences("learned",0).getAll().size()+" 种任务特征 · 仅保存在本机");}
    private void clearLearned(){if(BrowseService.instance!=null)BrowseService.instance.dismiss();getSharedPreferences("learned",0).edit().clear().apply();getSharedPreferences("scroll-overrides",0).edit().clear().apply();refresh();Toast.makeText(this,"学习记录及滑动标记已清除",Toast.LENGTH_SHORT).show();}
    private void executeCommand(){LocalCommand c=LocalCommand.parse(command.getText().toString());if(c.action.equals("invalid")){Toast.makeText(this,c.error,Toast.LENGTH_LONG).show();return;}if(c.action.equals("stop")){if(BrowseService.instance!=null)BrowseService.instance.dismiss();refresh();return;}if(c.action.equals("clear")){new android.app.AlertDialog.Builder(this).setMessage("清除本机学习记录？").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->clearLearned()).show();return;}open(c.pkg,c);}
    private void open(String pkg){
        open(pkg,null);
    }
    private void open(String pkg,LocalCommand config){
        if(!consent){Toast.makeText(this,"请先阅读并勾选授权说明",Toast.LENGTH_LONG).show();return;}
        BrowseService s=BrowseService.instance;
        if(s==null){Toast.makeText(this,"请先开启无障碍服务，再返回本页",Toast.LENGTH_LONG).show();return;}
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch==null){Toast.makeText(this,"未找到该应用，请先安装官方应用",Toast.LENGTH_LONG).show();return;}
        if(!s.prepare(pkg)){Toast.makeText(this,"控制条创建失败，请检查无障碍服务",Toast.LENGTH_LONG).show();return;}
        if(config!=null)s.configure(config);
        try{startActivity(launch);}catch(RuntimeException e){s.dismiss();Toast.makeText(this,"无法打开应用，请重新检查安装状态",Toast.LENGTH_LONG).show();}
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String value,int size,int color,int bottom){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);body.addView(t,lp);return t;}
    private void button(String label,Runnable action,boolean primary){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?Color.rgb(9,33,30):ink);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?accent:Color.rgb(32,48,63)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.bottomMargin=dp(14);body.addView(b,lp);}
}
