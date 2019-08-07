package cc.funkemunky.api.events;

import cc.funkemunky.api.Atlas;
import cc.funkemunky.api.events.exceptions.ListenParamaterException;
import javafx.collections.transformation.SortedList;
import lombok.Getter;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class EventManager {
    private final List<ListenerMethod> listenerMethods = new CopyOnWriteArrayList<>();
    private boolean paused = false;

    public void registerListener(Method method, AtlasListener listener, Plugin plugin) throws ListenParamaterException {
        if(method.getParameterTypes().length == 1) {
            if(method.getParameterTypes()[0].getSuperclass().equals(AtlasEvent.class)) {
                Listen listen = method.getAnnotation(Listen.class);
                ListenerMethod lm = new ListenerMethod(plugin, method, listener, listen.priority());

                if(!listen.priority().equals(ListenerPriority.NONE)) {
                    lm.setListenerPriority(listen.priority());
                }

                listenerMethods.add(lm);
                listenerMethods.sort(Comparator.comparing(mth -> mth.getListenerPriority().getPriority(), Comparator.reverseOrder()));
            } else {
                throw new ListenParamaterException("Method " + method.getDeclaringClass().getName() + "#" + method.getName() + "'s paramater: " + method.getParameterTypes()[0].getName() + " is not an instanceof " + AtlasEvent.class.getSimpleName() + "!");
            }
        } else {
            throw new ListenParamaterException("Method " + method.getDeclaringClass().getName() + "#" + method.getName() + " has an invalid amount of paramters (count=" + method.getParameterTypes().length + ")!");
        }
    }

    public void clearAllRegistered() {
        listenerMethods.clear();
    }

    public void unregisterAll(Plugin plugin) {
        listenerMethods.stream()
                .filter(lm -> lm.getPlugin().equals(plugin))
                .forEach(listenerMethods::remove);
    }

    public void unregisterListener(AtlasListener listener) {
        listenerMethods.stream().filter(lm -> lm.getListener().equals(listener)).forEach(listenerMethods::remove);
    }

    public void registerListeners(AtlasListener listener, Plugin plugin) {
        Arrays.stream(listener.getClass().getMethods()).filter(method -> method.isAnnotationPresent(Listen.class)).forEach(method -> {
            try {
                registerListener(method, listener, plugin);
            } catch(ListenParamaterException e) {
                e.printStackTrace();
            }
        });
    }

    public void callEvent(AtlasEvent event) {
        if(!paused && event != null) {
            Atlas.getInstance().getProfile().start("event:" + event.getClass().getSimpleName());
            for (ListenerMethod lm : listenerMethods) {
                if(lm.getMethod().getParameterTypes().length != 1 || !lm.getMethod().getParameterTypes()[0].getName().equals(event.getClass().getName())) continue;

                try {
                    lm.getMethod().invoke(lm.getListener(), event);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            Atlas.getInstance().getProfile().stop("event:" + event.getClass().getSimpleName());
        }
    }

    public void callEventAsync(AtlasEvent event) {
        Atlas.getInstance().getService().execute(() -> callEvent(event));
    }
}