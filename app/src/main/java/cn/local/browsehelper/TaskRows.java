package cn.local.browsehelper;

/** Some WebViews expose an entire task row as one accessibility label. */
public final class TaskRows {
    private static final String[] BUTTONS={"去浏览","去完成","逛一逛","去逛逛"};
    /** WebViews may insert spaces, line breaks or format characters between glyphs. */
    public static String normalize(String text){
        return java.text.Normalizer.normalize(text==null?"":text,java.text.Normalizer.Form.NFKC)
            .replaceAll("[\\s\\p{Z}\\p{Cf}]+","");
    }
    public static String action(String label){
        String t=normalize(label);
        String plain=t.replaceAll("[，,：: ]*(按钮|链接|button|link)$","").trim();
        for(String b:BUTTONS)if(plain.equals(b))return b;
        if(t.length()>220||!t.contains("浏览")||actionCount(t)!=1)return "";
        for(String b:BUTTONS)if(t.contains(b)&&TaskRules.isBrowseTask(t,b))return b;return "";
    }
    public static boolean merged(String label){String action=action(label);return !action.isEmpty()&&normalize(label).contains("浏览");}
    public static int actionCount(String text){text=normalize(text);int total=0;for(String b:BUTTONS){int p=0;while((p=text.indexOf(b,p))>=0){total++;p+=b.length();}}return total;}
    private TaskRows(){}
}
