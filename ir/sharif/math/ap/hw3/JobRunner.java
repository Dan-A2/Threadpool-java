package ir.sharif.math.ap.hw3;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JobRunner {

    private final HashMap<String, Integer> resourcesToBeUsed = new HashMap<>();
    private final LinkedList<Job> jobsToBeDone = new LinkedList<>();
    private ThreadPool pool;
    private MyLock lock;
    private Boss boss;

    public JobRunner(Map<String, Integer> resources, List<Job> jobs, int initialThreadNumber) {
        for (String name : resources.keySet()) {
            resourcesToBeUsed.put(name, resources.get(name));
        }
        jobsToBeDone.addAll(jobs);
        pool = new ThreadPool(initialThreadNumber);
        lock = new MyLock();
        boss = new Boss();
        boss.start();
    }

    public void setThreadNumbers(int threadNumbers) {
        lock.lock(1);
        boolean b = threadNumbers > pool.getThreadNumbers();
        pool.setThreadNumbers(threadNumbers);
        if(b){
            synchronized (boss) {
                boss.notifyAll();
            }
        }
        lock.release();
    }

    boolean isResourceAvailable(Job job) {
        int availableResources = 0;
        LinkedList<String> resourceNames = new LinkedList<>(resourcesToBeUsed.keySet());
        for (int i = 0; i < job.getResources().size(); i++) {
            for (int j = 0; j < resourceNames.size(); j++) {
                if (job.getResources().get(i).equals(resourceNames.get(j)) && resourcesToBeUsed.get(resourceNames.get(j)) > 0) {
                    availableResources++;
                    break;
                }
            }
        }
        return availableResources == job.getResources().size();
    }

    void reduceResources(Job job) {
        LinkedList<String> resourceNames = new LinkedList<>(resourcesToBeUsed.keySet());
        for (int i = 0; i < job.getResources().size(); i++) {
            for (int j = 0; j < resourceNames.size(); j++) {
                if (job.getResources().get(i).equals(resourceNames.get(j))) {
                    int i1 = resourcesToBeUsed.remove(resourceNames.get(j));
                    resourcesToBeUsed.put(resourceNames.get(j), i1 - 1);
                    break;
                }
            }
        }
    }

    void recoverResources(Job job) {
        LinkedList<String> resourceNames = new LinkedList<>(resourcesToBeUsed.keySet());
        for (int i = 0; i < job.getResources().size(); i++) {
            for (int j = 0; j < resourceNames.size(); j++) {
                if (job.getResources().get(i).equals(resourceNames.get(j))) {
                    int i1 = resourcesToBeUsed.remove(resourceNames.get(j));
                    resourcesToBeUsed.put(resourceNames.get(j), i1 + 1);
                    break;
                }
            }
        }
    }

    private class Task {
        private Job kooft;
        private boolean isFinished;
        private long waitingTime;

        public Task(Job kooft) {
            this.kooft = kooft;
            this.isFinished = false;
        }

        public void run() throws InterruptedException {
            waitingTime = kooft.getRunnable().run();
            lock.lock(2);
            Thread.sleep(waitingTime);
            recoverResources(kooft);
            synchronized (boss) {
                boss.notifyAll();
            }
            lock.release();
        }

        public boolean isFinished() {
            return isFinished;
        }
        public void setFinished(boolean finished) {
            isFinished = finished;
        }
        public long getWaitingTime() {
            return waitingTime;
        }
        public void setWaitingTime(long waitingTime) {
            this.waitingTime = waitingTime;
        }
    }

    private class Boss extends Thread {

        @Override
        public void run() {
            while (!jobsToBeDone.isEmpty()) {
                lock.lock(3);
                int inactiveCount = pool.getInactiveThreadNumbers();
                for (int i = 0; i < jobsToBeDone.size(); i++) {
                    Job currentJob = jobsToBeDone.get(i);
                    if (inactiveCount > 0 && isResourceAvailable(currentJob)) {
                        reduceResources(currentJob);
                        jobsToBeDone.remove(currentJob);
                        inactiveCount--;
                        i--;
                        pool.invokeLater(new Task(currentJob));
                    }
                }
                lock.release();
                synchronized (boss) {
                    try {
                        boss.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public class ThreadPool {

        private final LinkedList<PoolWorker> workers = new LinkedList<>(); // all threads are waiting here
        private final LinkedList<Task> queue = new LinkedList<>(); // tasks given by sadat wait here

        public ThreadPool(int threadNumbers) {
            for (int i = 0; i < threadNumbers; i++) {
                PoolWorker worker = new PoolWorker();
                workers.add(worker);
                worker.start();
            }
        }

        public int getThreadNumbers() {
            return workers.size();
        }

        public int getInactiveThreadNumbers() {
            int ans = 0;
            for (PoolWorker w : workers) {
                if (!w.isWorking) {
                    ans++;
                }
            }
            return ans;
        }

        public void setThreadNumbers(int threadNumbers) {
            synchronized (queue) {
                int diff = threadNumbers - getThreadNumbers();
                if (diff >= 0) {
                    for (int i = 0; i < diff; i++) {
                        PoolWorker worker = new PoolWorker();
                        workers.add(worker);
                        worker.start();
                    }
                } else {
                    diff *= -1;
                    for (int i = 0; i < diff; i++) {
                        workers.getFirst().stopIt();
                    }
                }
            }
        }

        public void invokeLater(Task task) {
            synchronized (queue) {
                queue.add(task);
                queue.notifyAll();
            }
        }

        private class PoolWorker extends Thread {

            private boolean isFinished = false;
            private boolean isWorking = false;

            @Override
            public void run() {
                long sleepTime;
                while(!isFinished){
                    Job job = null;
                    synchronized(queue){
                        if(!queue.isEmpty()) job = queue.removeFirst().kooft;
                        else{
                            try {
                                queue.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if(job!=null){
                        isWorking = true;
                        sleepTime = job.getRunnable().run();
                        lock.lock(2);
                        recoverResources(job);
                        try {
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException ignored) {}
                        isWorking = false;
                        lock.release();
                        synchronized(boss){
                            boss.notifyAll();
                        }
                    }
                }
            }

            public void stopIt() {
                synchronized (queue) {
                    isFinished = true;
                    queue.notifyAll();
                    workers.remove(this);
                }
            }
        }
    }

    public class MyLock {

        private final Object object;
        private volatile boolean free;
        private final int[] priorities = {0, 0, 0}; // ith index for ith priority

        public MyLock() {
            free = true;
            object = new Object();
        }

        void lock(int id) {
            synchronized (object) {
                priorities[id-1]++;
                while (!free || !checkPriority(id)) {
                    try {
                        object.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                priorities[id-1]--;
                free = false;
            }
        }

        void release() {
            synchronized (object) {
                free = true;
                object.notifyAll();
            }
        }

        synchronized boolean checkPriority(int currentId) {
            if (currentId == 1) {
                return true;
            } else if (currentId == 2) {
                return priorities[0] == 0;
            } else {
                if (priorities[0] > 0) {
                    return false;
                }
                if (priorities[1] > 0) {
                    return false;
                }
                return true;
            }
        }
    }
}