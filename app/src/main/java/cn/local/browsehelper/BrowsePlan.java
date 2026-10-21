package cn.local.browsehelper;

import java.util.*;

/** A confirmed plan bounds each task as well as the whole run. */
public final class BrowsePlan {
    public static final class Item {
        public final String id,title,signature;
        public final int remaining,seconds,initialProgress;
        public Item(String id,String row){
            this.id=id;TaskProgress p=TaskProgress.parse(row);
            title=p==null?row.replaceAll("去浏览|去完成|去逛逛|逛一逛","").trim():p.name;
            remaining=p==null?1:p.total-p.current;seconds=TaskRules.seconds(row);
            initialProgress=p==null?-1:p.current;
            signature=signature(row);
        }
    }
    private final Map<String,Item> items=new LinkedHashMap<>();
    private final Map<String,Integer> done=new HashMap<>();
    public void add(Item item){items.put(item.id,item);}
    public int total(){int sum=0;for(Item i:items.values())sum+=Math.max(0,i.remaining);return sum;}
    public boolean allows(String id,String row){Item i=items.get(id);if(i==null||!i.signature.equals(signature(row))||done.getOrDefault(id,0)>=i.remaining)return false;TaskProgress p=TaskProgress.parse(row);return i.initialProgress<0?p==null:p!=null&&p.current==i.initialProgress+done.getOrDefault(id,0);}
    public void confirmOne(String id){done.put(id,done.getOrDefault(id,0)+1);}
    public static String signature(String row){TaskProgress p=TaskProgress.parse(row);return TaskRules.seconds(row)+":"+(p==null?"single":p.total);}
}
