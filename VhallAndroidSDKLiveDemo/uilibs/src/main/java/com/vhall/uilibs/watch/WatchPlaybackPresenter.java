package com.vhall.uilibs.watch;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import com.vhall.business.ChatServer;
import com.vhall.business.MessageServer;
import com.vhall.business.VhallSDK;
import com.vhall.business.WatchLive;
import com.vhall.business.WatchPlayback;
import com.vhall.playersdk.player.vhallplayer.VHallPlayer;
import com.vhall.uilibs.Param;
import com.vhall.uilibs.R;
import com.vhall.uilibs.chat.ChatContract;
import com.vhall.uilibs.util.VhallUtil;
import com.vhall.uilibs.util.emoji.InputUser;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 观看回放的Presenter
 */
public class WatchPlaybackPresenter implements WatchContract.PlaybackPresenter, ChatContract.ChatPresenter {
    private static final String TAG = "PlaybackPresenter";
    private Param param;
    WatchContract.PlaybackView playbackView;
    WatchContract.DocumentView documentView;
    WatchContract.WatchView watchView;
    ChatContract.ChatView chatView;
    private WatchPlayback watchPlayback;

    int[] scaleTypeList = new int[]{WatchLive.FIT_DEFAULT, WatchLive.FIT_CENTER_INSIDE, WatchLive.FIT_X, WatchLive.FIT_Y, WatchLive.FIT_XY};
    int currentPos = 0;
    private int scaleType = WatchLive.FIT_DEFAULT;

    private int limit = 5;
    private int pos = 0;

    private long playerCurrentPosition = 0L;
    private long playerDuration;
    private String playerDurationTimeStr = "00:00:00";

    private boolean loadingVideo = false;
    private boolean loadingComment = false;

    private Timer timer;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (getWatchPlayback().isPlaying()) {
                playerCurrentPosition = getWatchPlayback().getCurrentPosition();
                playbackView.setSeekbarCurrentPosition((int) playerCurrentPosition);
//                String playerCurrentPositionStr = VhallUtil.converLongTimeToStr(playerCurrentPosition);
//                //playbackView.setProgressLabel(playerCurrentPositionStr + "/" + playerDurationTimeStr);
//                playbackView.setProgressLabel(playerCurrentPositionStr, playerDurationTimeStr);
            }
        }
    };

    public WatchPlaybackPresenter(WatchContract.PlaybackView playbackView, WatchContract.DocumentView documentView, ChatContract.ChatView chatView, WatchContract.WatchView watchView, Param param) {
        this.playbackView = playbackView;
        this.documentView = documentView;
        this.watchView = watchView;
        this.chatView = chatView;
        this.param = param;
        this.playbackView.setPresenter(this);
        this.chatView.setPresenter(this);
    }

    @Override
    public void start() {
        playbackView.setScaleTypeText(scaleType);
        initWatch();
    }

    private void initCommentData(int pos) {
        if (loadingComment)
            return;
        loadingComment = true;
        watchPlayback.requestCommentHistory(param.watchId, limit, pos, new ChatServer.ChatRecordCallback() {
            @Override
            public void onDataLoaded(List<ChatServer.ChatInfo> list) {
                loadingComment = false;
                chatView.notifyDataChanged(list);
            }

            @Override
            public void onFailed(int errorcode, String messaage) {
                loadingComment = false;
                watchView.showToast(messaage);
            }
        });
    }

    private void initWatch() {
        if (loadingVideo)
            return;
        loadingVideo = true;
        VhallSDK.getInstance().initWatch(param.watchId, param.userName, param.userCustomId, param.userVhallId, param.key, getWatchPlayback(), new VhallSDK.RequestCallback() {
            @Override
            public void onSuccess() {
                loadingVideo = false;
                handlePosition();
                pos = 0;
                initCommentData(pos);
                watchView.showNotice(getWatchPlayback().getNotice()); //显示公告
            }

            @Override
            public void onError(int errorCode, String reason) {
                loadingVideo = false;
                watchView.showToast(reason);
            }
        });
    }

    @Override
    public void onFragmentStop() {
        stopPlay();
    }

    @Override
    public void onFragmentDestory() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        getWatchPlayback().destory();
    }

    @Override
    public void startPlay() {
        if (!getWatchPlayback().isAvaliable())
            return;
        playbackView.setPlayIcon(false);
        getWatchPlayback().start();

    }

    @Override
    public void paushPlay() {
        getWatchPlayback().pause();
        playbackView.setPlayIcon(true);
    }

    @Override
    public void stopPlay() {
        getWatchPlayback().stop();
        playbackView.setPlayIcon(true);
    }

    @Override
    public void onPlayClick() {
        if (getWatchPlayback().isPlaying()) {
            paushPlay();
        } else {
            if (getWatchPlayback().isAvaliable()) {
                startPlay();
            } else {
                initWatch();
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        playbackView.setProgressLabel(VhallUtil.converLongTimeToStr(progress), playerDurationTimeStr);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        playerCurrentPosition = seekBar.getProgress();
        if (!getWatchPlayback().isPlaying()) {
            startPlay();
        }
        getWatchPlayback().seekTo(playerCurrentPosition);
    }

    @Override
    public int changeScaleType() {
        scaleType = scaleTypeList[(++currentPos) % scaleTypeList.length];
        getWatchPlayback().setScaleType(scaleType);
        playbackView.setScaleTypeText(scaleType);
        return scaleType;
    }

    @Override
    public int changeScreenOri() {
        return watchView.changeOrientation();
    }

    public WatchPlayback getWatchPlayback() {
        if (watchPlayback == null) {
            WatchPlayback.Builder builder = new WatchPlayback.Builder().context(watchView.getActivity()).containerLayout(playbackView.getContainer()).callback(new WatchCallback()).docCallback(new DocCallback());
            watchPlayback = builder.build();
        }
        return watchPlayback;
    }

    private class DocCallback implements WatchPlayback.DocumentEventCallback {

        @Override
        public void onEvent(String key, List<MessageServer.MsgInfo> msgInfos) {
            if (msgInfos != null && msgInfos.size() > 0) {
                documentView.paintPPT(key, msgInfos);
                documentView.paintBoard(key, msgInfos);
            }
        }

        @Override
        public void onEvent(MessageServer.MsgInfo msgInfo) {
            documentView.paintPPT(msgInfo);
            documentView.paintBoard(msgInfo);
        }
    }

    private class WatchCallback implements WatchPlayback.WatchEventCallback {
        @Override
        public void onStartFailed(String reason) {//开始播放失败
            Toast.makeText(watchView.getActivity(), reason, Toast.LENGTH_SHORT).show();
            playbackView.setPlayIcon(true);
        }

        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {//播放过程中的状态信息
            switch (playbackState) {
                case VHallPlayer.STATE_IDLE:
                    Log.e(TAG, "STATE_IDLE");
                    break;
                case VHallPlayer.STATE_PREPARING:
                    Log.e(TAG, "STATE_PREPARING");
                    playbackView.showProgressbar(true);
                    break;
                case VHallPlayer.STATE_BUFFERING:
                    Log.e(TAG, "STATE_BUFFERING");
                    playbackView.showProgressbar(true);
                    break;
                case VHallPlayer.STATE_READY:
                    playbackView.showProgressbar(false);
                    playerDuration = getWatchPlayback().getDuration();
                    playerDurationTimeStr = VhallUtil.converLongTimeToStr(playerDuration);
                    playbackView.setSeekbarMax((int) playerDuration);
                    Log.e(TAG, "STATE_READY");
                    break;
                case VHallPlayer.STATE_ENDED:
                    playbackView.showProgressbar(false);
                    Log.e(TAG, "STATE_ENDED");
                    playerCurrentPosition = 0;
                    getWatchPlayback().seekTo(0);
                    playbackView.setSeekbarCurrentPosition(0);
                    getWatchPlayback().pause();
                    playbackView.setPlayIcon(true);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onError(Exception e) {//播放出错
            playbackView.showProgressbar(false);
            stopPlay();
        }

        @Override
        public void onVideoSizeChanged(int width, int height) {//视频宽高改变
        }
    }

    //每秒获取一下进度
    private void handlePosition() {
        if (timer != null)
            return;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                handler.sendEmptyMessage(0);
            }
        }, 1000, 1000);
    }

    @Override
    public void showChatView(boolean emoji, InputUser user, int limit) {
        watchView.showChatView(emoji, user, limit);
    }

    @Override
    public void sendChat(final String text) {
        if (TextUtils.isEmpty(param.userVhallId)) {
            Toast.makeText(watchView.getActivity(), R.string.vhall_login_first, Toast.LENGTH_SHORT).show();
            return;
        }
        getWatchPlayback().sendComment(text, param.userVhallId, new VhallSDK.RequestCallback() {
            @Override
            public void onSuccess() {
                chatView.clearChatData();
                initCommentData(pos = 0);
            }

            @Override
            public void onError(int errorCode, String reason) {
                watchView.showToast(reason);
            }
        });
    }

    @Override
    public void sendQuestion(String content) {

    }

    @Override
    public void onLoginReturn() {

    }

    @Override
    public void onFreshData() {
        pos = pos + limit;
        initCommentData(pos);
    }

    @Override
    public void showSurvey(String surveyid) {

    }
}
