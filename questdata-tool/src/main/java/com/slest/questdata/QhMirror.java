package com.slest.questdata;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class QhMirror
{
    private static final String ANCHOR = "QuestHelperPlugin";
    private static final String ROOT = "com.questhelper.";

    private final ClassLoader loader;
    private final String root;

    private final Map<String, Class<?>> classes = new ConcurrentHashMap<>();
    private final Map<String, Method> methods = new ConcurrentHashMap<>();
    private final Map<String, Field> fields = new ConcurrentHashMap<>();
    private final Map<String, Boolean> absent = new ConcurrentHashMap<>();

    private QhMirror(ClassLoader loader, String root)
    {
        this.loader = loader;
        this.root = root;
    }

    public static QhMirror using(ClassLoader loader)
    {
        if (loader == null)
        {
            return null;
        }
        try
        {
            Class<?> type = Class.forName(ROOT + ANCHOR, false, loader);
            return new QhMirror(type.getClassLoader(), ROOT);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    public boolean isAvailable()
    {
        return cls("questinfo.QuestHelperQuest") != null;
    }

    public String getRoot()
    {
        return root;
    }

    public Class<?> cls(String relative)
    {
        Class<?> cached = classes.get(relative);
        if (cached != null)
        {
            return cached;
        }
        if (absent.containsKey("c:" + relative))
        {
            return null;
        }
        try
        {
            Class<?> type = Class.forName(root + relative, false, loader);
            classes.put(relative, type);
            return type;
        }
        catch (Throwable t)
        {
            absent.put("c:" + relative, Boolean.TRUE);
            return null;
        }
    }

    public boolean isA(Object value, String simpleName)
    {
        if (value == null || simpleName == null)
        {
            return false;
        }
        return isA(value.getClass(), simpleName);
    }

    private boolean isA(Class<?> type, String simpleName)
    {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
        {
            if (simpleName.equals(c.getSimpleName()))
            {
                return true;
            }
            for (Class<?> i : c.getInterfaces())
            {
                if (isA(i, simpleName))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public String typeOf(Object value)
    {
        return value == null ? null : value.getClass().getSimpleName();
    }

    public <T> T read(Object target, String name, Class<T> type)
    {
        if (target == null)
        {
            return null;
        }
        Field field = field(target.getClass(), name);
        if (field == null)
        {
            return null;
        }
        try
        {
            Object value = field.get(target);
            if (value == null)
            {
                return null;
            }
            return type.isInstance(value) ? type.cast(value) : null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private Field field(Class<?> type, String name)
    {
        String key = type.getName() + "#" + name;
        Field cached = fields.get(key);
        if (cached != null)
        {
            return cached;
        }
        if (absent.containsKey("f:" + key))
        {
            return null;
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass())
        {
            try
            {
                Field found = c.getDeclaredField(name);
                found.setAccessible(true);
                fields.put(key, found);
                return found;
            }
            catch (NoSuchFieldException ignored)
            {
            }
            catch (Throwable t)
            {
                break;
            }
        }
        absent.put("f:" + key, Boolean.TRUE);
        return null;
    }

    public boolean write(Object target, String name, Object value)
    {
        if (target == null)
        {
            return false;
        }
        Field field = field(target.getClass(), name);
        if (field == null)
        {
            return false;
        }
        try
        {
            field.set(target, value);
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    public Object allocate(String className)
    {
        try
        {
            Class<?> type = Class.forName(className, false, loader);
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            return unsafeType.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public Object stubClient(int skillLevel)
    {
        try
        {
            Class<?> type = Class.forName("net.runelite.api.Client", false, loader);
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (("getRealSkillLevel".equals(name) || "getBoostedSkillLevel".equals(name))
                                && method.getReturnType() == int.class)
                        {
                            return skillLevel;
                        }
                        return zeroOf(method.getReturnType());
                    });
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public Object stubQuestHelperPlugin(Object client, String accountType)
    {
        Object plugin = allocate(root + ANCHOR);
        Object states = allocate(root + "statemanagement.PlayerStateManager");
        if (plugin == null || states == null)
        {
            return null;
        }
        Object normal = enumConstant("domain.AccountType", accountType);
        if (normal != null)
        {
            write(states, "accountType", normal);
        }
        if (client != null)
        {
            write(states, "client", client);
        }
        return write(plugin, "playerStateManager", states) ? plugin : null;
    }

    public Object enumConstant(String relativeClass, String name)
    {
        for (Object constant : enumValues(relativeClass))
        {
            if (constant instanceof Enum<?> && ((Enum<?>) constant).name().equals(name))
            {
                return constant;
            }
        }
        return null;
    }

    public Object stubConfigManager()
    {
        return allocate("net.runelite.client.config.ConfigManager");
    }

    public Object call(Object target, String name)
    {
        if (target == null)
        {
            return null;
        }
        Method method = method(target.getClass(), name, 0);
        if (method == null)
        {
            return null;
        }
        try
        {
            return method.invoke(target);
        }
        catch (InvocationTargetException e)
        {
            return null;
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public Object callOrThrow(Object target, String name) throws Throwable
    {
        if (target == null)
        {
            return null;
        }
        Method method = method(target.getClass(), name, 0);
        if (method == null)
        {
            throw new NoSuchMethodException(target.getClass().getName() + "." + name + "()");
        }
        try
        {
            return method.invoke(target);
        }
        catch (InvocationTargetException e)
        {
            throw e.getCause() == null ? e : e.getCause();
        }
    }

    private Method method(Class<?> type, String name, int arity)
    {
        String key = type.getName() + "#" + name + "/" + arity;
        Method cached = methods.get(key);
        if (cached != null)
        {
            return cached;
        }
        if (absent.containsKey("m:" + key))
        {
            return null;
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass())
        {
            // getDeclaredMethods() order is unspecified and bridges share name and arity: pick stably
            Method best = null;
            for (Method m : c.getDeclaredMethods())
            {
                if (m.getParameterCount() != arity || !m.getName().equals(name))
                {
                    continue;
                }
                if (best == null || preferred(m, best))
                {
                    best = m;
                }
            }
            if (best != null)
            {
                try
                {
                    best.setAccessible(true);
                }
                catch (Throwable ignored)
                {
                }
                methods.put(key, best);
                return best;
            }
        }
        absent.put("m:" + key, Boolean.TRUE);
        return null;
    }

    private static boolean preferred(Method candidate, Method incumbent)
    {
        boolean candidateSynthetic = candidate.isBridge() || candidate.isSynthetic();
        boolean incumbentSynthetic = incumbent.isBridge() || incumbent.isSynthetic();
        if (candidateSynthetic != incumbentSynthetic)
        {
            return !candidateSynthetic;
        }
        return candidate.getReturnType().getName().compareTo(incumbent.getReturnType().getName()) < 0;
    }

    public Integer callInt(Object target, String name)
    {
        Object value = call(target, name);
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public int callInt(Object target, String name, int fallback)
    {
        Integer value = callInt(target, name);
        return value == null ? fallback : value;
    }

    public Boolean callBool(Object target, String name)
    {
        Object value = call(target, name);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    public boolean callBool(Object target, String name, boolean fallback)
    {
        Boolean value = callBool(target, name);
        return value == null ? fallback : value;
    }

    public String callString(Object target, String name)
    {
        Object value = call(target, name);
        return value == null ? null : String.valueOf(value);
    }

    /** Keeps null elements: a null substep is positional in the exported step list. */
    @SuppressWarnings("unchecked")
    public <T> List<T> callList(Object target, String name)
    {
        Object value = call(target, name);
        if (!(value instanceof Collection))
        {
            return Collections.emptyList();
        }
        List<T> out = new ArrayList<>();
        for (Object item : (Collection<Object>) value)
        {
            out.add((T) item);
        }
        return out;
    }

    public <T> List<T> callListNonNull(Object target, String name)
    {
        List<T> all = callList(target, name);
        List<T> out = new ArrayList<>(all.size());
        for (T item : all)
        {
            if (item != null)
            {
                out.add(item);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> callMap(Object target, String name)
    {
        Object value = call(target, name);
        if (!(value instanceof Map))
        {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>((Map<K, V>) value);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> readList(Object target, String name)
    {
        List<T> value = read(target, name, List.class);
        return value == null ? Collections.emptyList() : value;
    }

    public Object[] enumValues(String relativeClass)
    {
        Class<?> type = cls(relativeClass);
        if (type == null)
        {
            return new Object[0];
        }
        Object[] constants = type.getEnumConstants();
        return constants == null ? new Object[0] : constants;
    }

    public Object defaultConfig()
    {
        return defaultsProxy("QuestHelperConfig");
    }

    public Object defaultsProxy(String relativeClass)
    {
        Class<?> iface = cls(relativeClass);
        if (iface == null || !iface.isInterface())
        {
            return null;
        }
        try
        {
            return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                    (proxy, method, args) -> {
                        if (method.isDefault())
                        {
                            return MethodHandles.lookup()
                                    .findSpecial(iface, method.getName(),
                                            MethodType.methodType(method.getReturnType(),
                                                    method.getParameterTypes()),
                                            iface)
                                    .bindTo(proxy)
                                    .invokeWithArguments(args == null ? new Object[0] : args);
                        }
                        return zeroOf(method.getReturnType());
                    });
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    private static Object zeroOf(Class<?> type)
    {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type == String.class) return "";
        // a real array, not null: callers index straight into it (e.g. getIntStack())
        if (type.isArray()) return java.lang.reflect.Array.newInstance(type.getComponentType(), STACK_SIZE);
        return null;
    }

    private static final int STACK_SIZE = 1000;

    public static String nameOf(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Enum<?>)
        {
            return ((Enum<?>) value).name();
        }
        return value.toString();
    }
}
