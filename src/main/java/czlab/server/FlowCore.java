/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package czlab.wflow.server;

import java.util.concurrent.ConcurrentHashMap;
import czlab.xlib.Activable;
import czlab.xlib.Identifiable;
import czlab.xlib.CU;
import czlab.xlib.Schedulable;
import czlab.xlib.TCore;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;


/**
 *
 * @author kenl
 *
 */
@SuppressWarnings({ "rawtypes", "unchecked"})
public class FlowCore implements Schedulable, Activable {

  public static FlowCore apply() { return new FlowCore(); }

  private Timer _timer;
  private Map _holdQ;
  private Map _runQ;
  private TCore _core;
  private String _id;

  private FlowCore() {
    _id= "FlowScheduler#" + CU.nextSeqInt();
  }

  public void activate(Object options) {
    Properties props= (Properties) options;
    _core = new TCore(_id,
        (int) props.getOrDefault("threads", 1),
        (boolean) props.getOrDefault("trace", true));
    _timer= new Timer (_id, true);
    _holdQ= new ConcurrentHashMap();
    _runQ= new ConcurrentHashMap();
    _core.start();
  }

  public void deactivate() {
    _timer.cancel();
    _holdQ.clear();
    _runQ.clear();
    _core.stop();
  }

  private void addTimer(Runnable w, long delay) {
    FlowCore me= this;
    _timer.schedule(new TimerTask() {
      public void run() {
        me.wakeup(w);
      }
    }, delay);
  }

  private Object xrefPid(Runnable w) {
    if (w instanceof Identifiable) {
      return ((Identifiable)w).id();
    } else {
      return null;
    }
  }

  @Override
  public void postpone(Runnable w, long delayMillis) {
    if (delayMillis < 0L) {
      hold(w);
    }
    else
    if (delayMillis == 0L) {
      run(w);
    }
    else {
      addTimer(w, delayMillis);
    }
  }

  @Override
  public void dequeue(Runnable w) {
    Object pid = xrefPid(w);
    if (pid != null) {
      _runQ.remove(pid);
    }
  }

  private void preRun(Runnable w) {
    Object pid = xrefPid(w);
    if (pid != null) {
      _holdQ.remove(pid);
      _runQ.put(pid,w);
    }
  }

  @Override
  public void run(Runnable w) {
    if (w != null) {
      preRun(w);
      _core.schedule(w);
    }
  }

  @Override
  public void hold(Object pid, Runnable w) {
    if (pid != null && w != null) {
      _runQ.remove(pid,w);
      _holdQ.put(pid, w);
    }
  }

  @Override
  public void hold(Runnable w) {
    hold(xrefPid(w), w);
  }

  @Override
  public void dispose() {
    deactivate();
    _core.dispose();
  }

  @Override
  public void wakeAndRun(Object pid, Runnable w) {
    if (pid != null && w != null) {
      _holdQ.remove(pid);
      _runQ.put(pid, w);
      run(w);
    }
  }

  @Override
  public void wakeup(Runnable w) {
    wakeAndRun(xrefPid(w), w);
  }

  @Override
  public void reschedule(Runnable w) {
    run(w);
  }

}


