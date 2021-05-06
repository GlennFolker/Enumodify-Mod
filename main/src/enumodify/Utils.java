package enumodify;

import arc.struct.*;
import arc.util.*;

import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.*;
import java.lang.reflect.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class Utils {
    private static final MethodHandle MODIFIERS;
    private static final MethodHandle DECL_CLASS;
    private static final MethodHandle SET_ACCESSOR;

    private static final boolean USE_SUN;
    private static Object J8_REF_FACTORY;
    private static MethodHandle J8_NEW_CONS_ACCESSOR;
    private static MethodHandle J8_NEW_INSTANCE;

    private static final int MODES;

    static {
        try {
            boolean use = false;
            try {
                Method getReflectionFactory = Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("getReflectionFactory");
                J8_REF_FACTORY = getReflectionFactory.invoke(null);

                Lookup lookup = MethodHandles.lookup();
                J8_NEW_CONS_ACCESSOR = lookup.unreflect(Class.forName("sun.reflect.ReflectionFactory").getDeclaredMethod("newConstructorAccessor", Constructor.class));
                J8_NEW_INSTANCE = lookup.unreflect(Class.forName("sun.reflect.ConstructorAccessor").getDeclaredMethod("newInstance", Object[].class));

                use = true;
            } catch(Exception e) {
                use = false;
            }
            USE_SUN = use;

            MODIFIERS = newLookup(Field.class).findSetter(Field.class, "modifiers", int.class);
            DECL_CLASS = newLookup(Constructor.class).findSetter(Constructor.class, "clazz", Class.class);

            Method setf = null;
            Method[] methodsf = Field.class.getDeclaredMethods();
            for(Method method : methodsf) {
                if(method.getName().contains("setFieldAccessor")) {
                    setf = method;
                    break;
                }
            }

            SET_ACCESSOR = newLookup(Field.class).unreflect(setf);

            int modes = 0;
            try {
                Field field = Lookup.class.getDeclaredField("FULL_POWER_MODES");
                field.setAccessible(true);

                modes = field.getInt(null);
            } catch(NoSuchFieldException e) {
                Field field = Lookup.class.getDeclaredField("ALL_MODES");
                field.setAccessible(true);

                modes = field.getInt(null);
            }
            MODES = modes;

            Log.info("Reflection/invocation utility has been initialized.");
            Log.info("Usage of sun packages: @.", USE_SUN);
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Lookup newLookup(Class<?> in) {
        Seq<Class<?>> argTypes = new Seq<>(Class.class);
        argTypes.addAll(Class.class, Class.class, int.class);

        Seq args = new Seq();
        args.addAll(in, null, MODES);

        try{
            Lookup.class.getDeclaredField("prevLookupClass");
        }catch(NoSuchFieldException e){
            argTypes.remove(1);
            args.remove(1);
        }

        try {
            Constructor<Lookup> cons = Lookup.class.getDeclaredConstructor(argTypes.toArray());
            cons.setAccessible(true);

            Field name = Class.class.getDeclaredField("name");
            String prev = in.getName();

            name.set(in, prev.substring(prev.lastIndexOf("."), prev.length()));
            Lookup lookup = cons.newInstance(args.toArray());
            name.set(in, prev);

            return lookup;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Object getConstructorAccessor(Class<T> enumClass, Constructor<T> constructor) {
        try {
            return J8_NEW_CONS_ACCESSOR.invoke(J8_REF_FACTORY, constructor);
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void revoke(Field field, int modifier) {
        try {
            field.setAccessible(true);

            int mods = field.getModifiers();
            MODIFIERS.invoke(field, mods & ~modifier);

            SET_ACCESSOR.invoke(field, null, false);
            SET_ACCESSOR.invoke(field, null, true);
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T createEnum(Class<T> type, Constructor<T> cons, Object... args) {
        try {
            if(USE_SUN){
                return type.cast(J8_NEW_INSTANCE.invoke(getConstructorAccessor(type, cons), args));
            }else{
                cons.setAccessible(true);

                Class<T> before = cons.getDeclaringClass();
                DECL_CLASS.invoke(cons, Object.class);

                T obj = cons.newInstance(args);
                DECL_CLASS.invoke(cons, before);

                return obj;
            }
        } catch(Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T addEntry(Class<T> type, String name) {
        if(!Enum.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("must be enum");
        }

        Field valuesField = null;
        Field[] fields = type.getDeclaredFields();
        for(Field field : fields) {
            if(field.getName().contains("$VALUES")) {
                valuesField = field;
                break;
            }
        }

        if(valuesField == null) throw new IllegalStateException("values field is null");
        try {
            valuesField.setAccessible(true);

            T[] previousValues = (T[])valuesField.get(type);
            Seq<T> values = new Seq<>(type);
            values.addAll(previousValues);

            Constructor<T> cons = type.getDeclaredConstructor(String.class, int.class);
            T value = createEnum(type, cons, name, previousValues.length);
            values.add(value);

            revoke(valuesField, Modifier.FINAL);

            valuesField.set(null, values.toArray());

            return value;
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}
