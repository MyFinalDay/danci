package org.ling0322.danci;

import java.util.*;

import android.app.Activity;
import android.database.*;
import android.database.sqlite.*;
import android.util.Log;
import android.util.Pair;

class UpdateDatabase implements Runnable {
    private ArrayList<Pair<String, Boolean>> answerList;
    private WordlistModel wl;
    public UpdateDatabase(Activity activity, ArrayList<Pair<String, Boolean>> answerList) {
        this.answerList = answerList;
        this.wl = new WordlistModel(activity);
    }
    public void run() {
        wl.answerList(answerList);
    }
    
}

public class Recite {

    public final int WORDS_PER_UNIT = 30;
    public final int MIN_NEW_WORDS = 5;
    public final int MIN_REVIEW_WORDS = 10;
    
    private final int ST_FINISHED = 0;
    private final int ST_NEW = 1;
    private final int ST_REVIEW = 2;
    private final int ST_DRILL = 3;

    private Thread updateThread;
    private ArrayList<String> wordList = new ArrayList<String>();
    private ArrayList<String> drillList = new ArrayList<String>();
    private ArrayList<String> sqlUpdateBuffer= new ArrayList<String>();
    private int cntState = ST_FINISHED;
    private String cntWord = "";
    private int totalWord = -1;
    private int newWordCount = 0;
    private int reviewWordCount = 0;
    private WordlistModel wldb;
    private ArrayList<Pair<String, Boolean>> answerList;
    private Activity mActivity;

    public Recite(Activity activity) {
        answerList = new ArrayList<Pair<String, Boolean>>();
        wldb = new WordlistModel(activity);
        mActivity = activity;
        if (wldb.isNullDbConn() == true)
            return ;
        totalWord = wldb.totalWord();
    }
    

    public boolean isNullDbConn() {
        return wldb.isNullDbConn();
    }
    
    public void finallize() {
    }
    
    public String getCntState() {
        switch (cntState) {
        case ST_FINISHED:
            int unpass = wldb.getProgress();
            String cntwl = Wordlist.currentWordList(mActivity);
            return String.format(
                "%s→当前进度: %d/%d %d%%",
                cntwl,
                totalWord - unpass,
                totalWord,
                (int)((totalWord - unpass) * 100 / totalWord)
                );
        case ST_NEW:
            return String.format("开始→[新单词 - %d/%d]→复习→巩固", wordList.size(), newWordCount);
        case ST_REVIEW:
            return String.format("开始→新单词→[复习 - %d/%d]→巩固", wordList.size(), reviewWordCount);
        case ST_DRILL:
            return String.format("开始→新单词→复习→[巩固 - %d]", wordList.size());
        }
        
        return "";
    }
    
    public void finish() {
        cntState = ST_FINISHED;
    }
    
    public void start() {
        int reviewWords = wldb.reviewCount();
        if (cntState == ST_FINISHED) {
            cntState = ST_NEW;
            wordList.clear();
            int minReviewWords = Math.min(MIN_REVIEW_WORDS, reviewWords);
            int nNewWords = Math.max(MIN_NEW_WORDS, WORDS_PER_UNIT - minReviewWords - reviewWords / 10);
            if (reviewWords < 300) {
                wordList = wldb.newWordList(nNewWords);
                newWordCount = wordList.size();
            }
            switchState();
        }
    }
    
    @SuppressWarnings("unchecked")
    private void switchState() {
        if (wordList.size() == 0) {
            switch(cntState) {
            case ST_NEW:
                wordList = wldb.reviewList(WORDS_PER_UNIT - newWordCount);
                reviewWordCount = wordList.size();
                cntState = ST_REVIEW;
                switchState();
                break;
            case ST_REVIEW:
                wordList = drillList;
                cntState = ST_DRILL;
                //
                // During the drill period, there is no database IO, so we could
                // now start to write back to database
                //
                updateThread = new Thread(new UpdateDatabase(mActivity, answerList));
                updateThread.start();
                sqlUpdateBuffer.clear();
                switchState();
                break;
            case ST_DRILL:
                cntState = ST_FINISHED;
                //
                // Here, we start to use database IO again, so we should wait updateThread
                // to finish HERE
                //
                try {
                    updateThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                drillList.clear();
                wordList.clear();
                answerList.clear();
            }
        }
    }
    
    public String pickWord() {
        cntWord = wordList.get(0);
        return cntWord;
    }
    
    public boolean isFinsihed() {
        if (cntState == ST_FINISHED) {
            return true;
        } else {
            return false;
        }
    }
    
    public void answer(boolean correct) {
        if (wordList.size() == 0)
            return ;
        
        if (cntState == ST_DRILL) {
            wordList.remove(0);
            if (correct == false) {
                wordList.add(cntWord);
            }
            switchState();
        } else {
            wordList.remove(0);
            if (correct == false)
                drillList.add(cntWord);
            answerList.add(new Pair<String, Boolean>(cntWord, correct));
            switchState();
        }
    }
}
