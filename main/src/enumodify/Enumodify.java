package enumodify;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.mod.*;
import enumodify.gen.*;

import java.lang.reflect.*;

import static mindustry.Vars.*;

@SuppressWarnings("unchecked")
public class Enumodify extends Mod {
    public Enumodify() {
        Events.on(FileTreeInitEvent.class, e -> ModSounds.load());
        Events.on(DisposeEvent.class, e -> ModSounds.dispose());
    }

    @Override
    public void loadContent() {
        try {
            Seq<ContentType> all = new Seq<>(ContentType.class);
            all.addAll(ContentType.all);

            Log.info(all);

            ContentType test = Utils.addEntry(ContentType.class, "enumodify_test");
            Log.info(test);

            all.add(test);

            Field allf = ContentType.class.getDeclaredField("all");
            allf.setAccessible(true);

            Utils.revoke(allf, Modifier.FINAL);

            allf.set(null, all.toArray());

            Field contentNameMapf = ContentLoader.class.getDeclaredField("contentNameMap");
            contentNameMapf.setAccessible(true);

            ObjectMap<String, MappableContent>[] contentNameMap = (ObjectMap<String, MappableContent>[])contentNameMapf.get(content);

            Seq<ObjectMap<String, MappableContent>> contentNameMapAll = Seq.of(true, ContentType.all.length, (Class<ObjectMap<String, MappableContent>>)contentNameMap.getClass().getComponentType());
            contentNameMapAll.addAll(contentNameMap);
            for(ContentType type : ContentType.all){
                if(contentNameMapAll.size <= type.ordinal()){
                    contentNameMapAll.add(new ObjectMap<>());
                }
            }

            contentNameMapf.set(content, contentNameMapAll.toArray());

            Field contentMapf = ContentLoader.class.getDeclaredField("contentMap");
            contentMapf.setAccessible(true);

            Seq<Content>[] contentMap = (Seq<Content>[])contentMapf.get(content);

            Seq<Seq<Content>> contentMapAll = Seq.of(true, ContentType.all.length, (Class<Seq<Content>>)contentMap.getClass().getComponentType());
            contentMapAll.addAll(contentMap);
            for(ContentType type : ContentType.all){
                if(contentMapAll.size <= type.ordinal()){
                    contentMapAll.add(new Seq<>());
                }
            }

            contentMapf.set(content, contentMapAll.toArray());

            Log.info(Seq.with(ContentType.all));
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
