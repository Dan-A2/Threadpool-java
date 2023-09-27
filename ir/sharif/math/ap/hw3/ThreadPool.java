package ir.sharif.math.ap.hw3;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;

public class ThreadPool {

    private final LinkedList<PoolWorker> company = new LinkedList<>(); // all threads are waiting here
    private final LinkedList<Task> queue = new LinkedList<>(); // tasks given by sadat wait here

    public ThreadPool(int threadNumbers) {
        for (int i = 0; i < threadNumbers; i++) {
            PoolWorker worker = new PoolWorker();
            company.add(worker);
            worker.start();
        }
    }

    public int getThreadNumbers() {
        return company.size();
    }

    public void setThreadNumbers(int threadNumbers){
        synchronized (company) {
            int diff = threadNumbers - getThreadNumbers();
            if (diff >= 0) {
                for (int i = 0; i < diff; i++) {
                    PoolWorker worker = new PoolWorker();
                    company.add(worker);
                    worker.start();
                }
            } else {
                diff *= -1;
                for (int i = 0; i < diff; i++) {
                    company.getFirst().stopIt();
                }
            }
        }
    }

    public void invokeLater(Runnable runnable) {
        Task task = new Task(runnable);
        synchronized (queue) {
            queue.add(task);
            queue.notifyAll();
        }
    }

    public void invokeAndWait(Runnable runnable) throws InterruptedException, InvocationTargetException {
        Task task = new Task(runnable);
        synchronized (queue) {
            queue.add(task);
            queue.notifyAll();
        }
        while (true) {
            synchronized (task) {
                task.wait();
                if (task.getError() != null) {
                    throw new InvocationTargetException(task.getError());
                }
                if (task.isFinished()) {
                    break;
                }
            }
        }
    }

    public void invokeAndWaitUninterruptible(Runnable runnable) throws InvocationTargetException {
        Task task = new Task(runnable);
        synchronized (queue) {
            queue.add(task);
            queue.notifyAll();
        }
        while (true) {
            synchronized (task) {
                try {
                    task.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
                if (task.getError() != null) {
                    throw new InvocationTargetException(task.getError());
                }
                if (task.isFinished()) {
                    break;
                }
            }
        }
    }

    private class PoolWorker extends Thread{

        private boolean isDead;

        public PoolWorker() {
            this.isDead = false;
        }

        @Override
        public void run() {
            while (!isDead) {
                Task task = null;
                synchronized (queue) {
                    while (queue.isEmpty()) {
                        if (isDead) {
                            return;
                        }
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (isDead) {
                        return;
                    }
                    task = queue.getFirst();
                    queue.removeFirst();
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    synchronized (task) {
                        task.setError(t);
                        task.notifyAll();
                    }
                }
                synchronized (task) {
                    task.setFinished(true);
                    task.notifyAll();
                    if (isDead) {
                        return;
                    }
                }
            }
        }

        public void stopIt() {
            synchronized (queue) {
                isDead = true;
                queue.notifyAll();
                company.remove(this);
            }
        }
    }

    private class Task {

        private Throwable error;
        private Runnable kooft;
        private boolean isFinished;

        public Task(Runnable kooft) {
            this.kooft = kooft;
            this.error = null;
            this.isFinished = false;
        }

        public void run() {
            kooft.run();
        }

        public Throwable getError() {
            return error;
        }

        public void setError(Throwable error) {
            this.error = error;
        }

        public boolean isFinished() {
            return isFinished;
        }

        public void setFinished(boolean finished) {
            isFinished = finished;
        }
    }

}