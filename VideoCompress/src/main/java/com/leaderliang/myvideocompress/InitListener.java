package com.leaderliang.myvideocompress;

/**
 * Created by karan on 13/2/15.
 */
public interface InitListener {
    void onLoadSuccess();

    void onLoadFail(String reason);
}
