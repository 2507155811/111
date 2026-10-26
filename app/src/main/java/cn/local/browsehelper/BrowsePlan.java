package cn.local.browsehelper;

import java.util.*;

/** A confirmed plan bounds each task as well as the whole run. */
public final class BrowsePlan {
    public static final class Item {
        public final String id,title,signature,row;
        public final int remaining,seconds,initialProgress;
        public final boolean forceScroll;
        public Item(String id,String row){
            this(id,row,false);
        }
        public Item(String id,String row,boolean forceScroll){
            this.forceScroll=forceScroll;
            this.row=row;
            this.id=id;TaskProgress p=TaskProgress.parse(row);
            title=p==null?row.replaceAll("去浏览|去完成|去逛逛|逛一逛","").trim():p.name;
            remaining=p==null?1:p.total-p.current;seconds=TaskRules.seconds(row);
            initialProgress=p==null?-1:p.current;
            signature=signature(row);
        }
    }
    private final Map<String,Item> items=new LinkedHashMap<>();
    private final Map<String,Integer> done=new HashMap<>();
    private final Set<String> skipped=new HashSet<>();
    private final Set<String> excluded=new HashSet<>();
    private final Set<String> retried=new HashSet<>();
    private final Set<String> recovered=new HashSet<>();
    public void add(Item item){items.put(item.id,item);}
    public void exclude(String id){excluded.add(id);}
    public boolean contains(String id){return items.containsKey(id);}
    public Set<String> excludedIds(){return new HashSet<>(excluded);}
    public boolean retryOnce(String id){return items.containsKey(id)&&!skipped.contains(id)&&retried.add(id);}
    /** Refresh observed list progress without crediting unverified actions as successes. */
    public boolean refreshObserved(Item observed,boolean allowNew){
        Item old=items.get(observed.id);
        if(old==null)return addDiscovered(observed,allowNew);
        if(excluded.contains(observed.id))return false;
        if(!old.signature.equals(observed.signature)){
            TaskProgress before=TaskProgress.parse(old.row),after=TaskProgress.parse(observed.row);
            // The opted-in activity may replace a round with a different total (10 -> 5).
            if(!allowNew||before==null||after==null||before.total==after.total
                ||!before.name.equals(after.name)||old.seconds!=observed.seconds
                ||!TaskRules.isBrowseTask(observed.row,TaskRows.action(observed.row)))return false;
            items.put(old.id,new Item(old.id,observed.row,old.forceScroll));
            done.remove(old.id);skipped.remove(old.id);retried.remove(old.id);
            return true;
        }
        TaskProgress p=TaskProgress.parse(observed.row);
        if(p!=null){
            int consumed=p.current-old.initialProgress;
            if(consumed<done.getOrDefault(old.id,0)||consumed>old.remaining)return false;
            if(consumed>done.getOrDefault(old.id,0)){done.put(old.id,consumed);skipped.remove(old.id);}
        }
        return allows(observed.id,observed.row);
    }
    /** One cooldown recovery per exact counter, never an unbounded retry loop. */
    public boolean recoverVisible(String id,String row){
        TaskProgress p=TaskProgress.parse(row);
        if(p==null||!skipped.contains(id)||excluded.contains(id))return false;
        Item i=items.get(id);if(i==null||!i.signature.equals(signature(row))
            ||p.current!=i.initialProgress+done.getOrDefault(id,0)||!p.incomplete())return false;
        if(!recovered.add(id+":"+p.attemptKey()))return false;
        skipped.remove(id);return true;
    }
    public Set<String> knownIds(){Set<String> ids=new HashSet<>(items.keySet());ids.addAll(excluded);return ids;}
    /** Only opt-in pure browsing may extend a running plan. Never reset existing quotas. */
    public boolean addDiscovered(Item item,boolean enabled){
        if(!enabled||items.containsKey(item.id)||excluded.contains(item.id)||item.remaining<1)return false;
        String action=TaskRows.action(item.row);
        if(action.isEmpty()||!TaskRules.isBrowseTask(item.row,action))return false;
        items.put(item.id,item);return true;
    }
    public int total(){int sum=0;for(Item i:items.values())sum+=Math.max(0,i.remaining);return sum;}
    public int pendingCount(){int n=0;for(Item i:items.values())if(!skipped.contains(i.id))n+=Math.max(0,i.remaining-done.getOrDefault(i.id,0));return n;}
    public int executionLimit(int requested){return requested>0?Math.min(requested,total()):total();}
    /** Return detection accepts known rows even when their quota is exhausted. */
    public boolean recognizes(String id,String row){
        Item i=items.get(id);if(i==null)return false;
        if(i.signature.equals(signature(row)))return true;
        TaskProgress initial=TaskProgress.parse(i.row),after=TaskProgress.parse(row);
        // Finished rows can hide their original duration and action button.
        return initial!=null&&after!=null&&initial.name.equals(after.name)&&initial.total==after.total
            &&TaskRules.seconds(row)<0&&TaskRules.rowCompleted(row);
    }
    public boolean allows(String id,String row){Item i=items.get(id);if(i==null||skipped.contains(id)||!i.signature.equals(signature(row))||done.getOrDefault(id,0)>=i.remaining)return false;TaskProgress p=TaskProgress.parse(row);return i.initialProgress<0?p==null:p!=null&&p.current==i.initialProgress+done.getOrDefault(id,0);}
    public void confirmOne(String id){done.put(id,done.getOrDefault(id,0)+1);}
    /** Consume externally observed increments too, so the approved quota cannot grow. */
    public boolean confirmProgress(String id,TaskProgress after){
        Item item=items.get(id);
        if(item==null||after==null||item.initialProgress<0||skipped.contains(id))return false;
        TaskProgress initial=TaskProgress.parse(item.row);
        int consumed=after.current-item.initialProgress;
        if(!after.advancedFrom(initial)||consumed<=done.getOrDefault(id,0)||consumed>item.remaining)return false;
        done.put(id,consumed);return true;
    }
    public String blockReason(String id,String row){
        Item i=items.get(id);
        if(excluded.contains(id))return "已取消勾选";
        if(i==null)return "尚未加入计划";
        if(skipped.contains(id)){TaskProgress p=TaskProgress.parse(row);return p!=null&&recovered.contains(id+":"+p.attemptKey())?"复试仍未确认":"暂缓，等待冷却复试";}
        if(!i.signature.equals(signature(row)))return "时长或总次数变化";
        if(done.getOrDefault(id,0)>=i.remaining)return "本轮额度已用完";
        TaskProgress p=TaskProgress.parse(row);
        if(i.initialProgress>=0&&(p==null||p.current!=i.initialProgress+done.getOrDefault(id,0)))return "进度尚未同步";
        return "可执行";
    }
    public void skip(String id){if(items.containsKey(id))skipped.add(id);}
    public int skippedCount(){return skipped.size();}
    public boolean forceScroll(String id){Item item=items.get(id);return item!=null&&item.forceScroll;}
    public static String signature(String row){TaskProgress p=TaskProgress.parse(row);return TaskRules.seconds(row)+":"+(p==null?"single":p.total);}
}
