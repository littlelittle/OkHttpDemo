package com.qingmu.okutils.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by Administrator on 2017/1/8.
 */

public class OKUtils {
    //OKHttpClient要求单例模式。
    private static OkHttpClient client;
    private static Call call;
    private static Handler handler;

    /**
     * @param context
     * @return Cache 缓存对象。可以依据项目的要求自行更改缓存目录。
     */
    private static Cache newCache(Context context) {
        int cacheSize = 10 * 1024 * 1024; // 10 MiB
        String okhttpCachePath = context.getCacheDir().getPath() + File.separator + "okhttp";
        File okhttpCache = new File(okhttpCachePath);
        if (!okhttpCache.exists()) {
            okhttpCache.mkdirs();
        }
        Cache cache = new Cache(okhttpCache, cacheSize);
        return cache;
    }

    /**
     * 本util仅提供异步请求请求。
     *
     * @param context
     * @param okCallBack 回调对象
     */
    private static <T> void netCore(Context context, Request request, final OKCallBack<T> okCallBack, final boolean isList, final Class clazz) {
        if (client == null)
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .cache(newCache(context))
                    .build();

        //handler
        if (null == handler) {

            handler = new Handler(context.getMainLooper());
        }

        call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final IOException err = e;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        okCallBack.onFail(err);
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (clazz != null) {

                    if (!isList) {

                        final T data = (T) JSON.parseObject(getText(response), clazz);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {

                                okCallBack.onSuccessBody(data);
                            }
                        });


                    } else {

                        final List<T> list = JSON.parseArray(getText(response), clazz);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {

                                okCallBack.onSuccessBody(list);
                            }
                        });

                    }
                } else {
                    throw new RuntimeException("clazz can't be null!");
                }

                okCallBack.onSuccessHeaders(response.headers().toMultimap());
            }

            private String getText(Response response) {
                String callback = null;
                try {
                    callback = response.body().string();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return callback;
            }
        });
    }

    /**
     * Get请求
     *
     * @param context
     * @param url        要求url要自己拼接。
     * @param okCallBack
     */
    public static <T> void Get(Context context, String url,
                               Map<String, String> requestParams,
                               Map<String, String> headers, OKCallBack<T> okCallBack, boolean isList, Class<T> clazz) {
        //1.1： 添加请求头
        Request.Builder requsetBuilder = new Request.Builder();
        if (headers != null)
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requsetBuilder.addHeader(header.getKey(), header.getValue());
            }

        //1.2: 拼接请求参数
        String completedUrl = url;
        if (requestParams != null) {
            StringBuilder stringBuilder = new StringBuilder();
            for (Map.Entry<String, String> requestParam : requestParams.entrySet()) {
                stringBuilder.append("&");
                stringBuilder.append(requestParam.getKey());
                stringBuilder.append("=");
                stringBuilder.append(requestParam.getValue());
            }
            stringBuilder.deleteCharAt(0);
            completedUrl = completedUrl + "?" + stringBuilder.toString();
        }

        //1.3: 创建请求对象
        Request request = null;
        if (isNetworkConnected(context)) {
            request = requsetBuilder
                    .url(completedUrl)
                    .build();

        } else {
            request = requsetBuilder
                    .url(completedUrl)
                    .cacheControl(CacheControl.FORCE_CACHE)//没网强制走缓存。
                    .build();
        }

        //2: 执行请求
        netCore(context, request, okCallBack, isList, clazz);
    }


    public static <T> void Post(Context context, String url,
                                Map<String, String> headers, Map<String, String> forms,
                                OKCallBack<T> okCallBack, boolean islist, Class<T> clazz) {
        //1.1：添加表单数据
        FormBody.Builder formBuilder = new FormBody.Builder();
        if (forms != null)
            for (Map.Entry<String, String> form : forms.entrySet()) {
                formBuilder.add(form.getKey(), form.getValue());
            }
        //1.2: 创建请求体对象
        FormBody formBody = formBuilder.build();

        //2.1: 添加请求头
        Request.Builder requestBuilder = new Request.Builder();
        if (headers != null)
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.addHeader(header.getKey(), header.getValue());
            }
        //2.2: 创建请求对象
        Request request = null;
        if (isNetworkConnected(context)) {
            request = requestBuilder
                    .url(url)
                    .post(formBody)
                    .build();

        } else {
            request = requestBuilder
                    .url(url)
                    .cacheControl(CacheControl.FORCE_CACHE)//没网强制走缓存。
                    .post(formBody)
                    .build();
        }

        //3: 执行请求
        netCore(context, request, okCallBack, islist, clazz);
    }


    /**
     * 获取网络状态。
     *
     * @param context
     * @return
     */
    private static boolean isNetworkConnected(Context context) {
        if (context != null) {
            ConnectivityManager mConnectivityManager = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
            if (mNetworkInfo != null) {
                return mNetworkInfo.isAvailable();
            }
        }
        return false;
    }

    /**
     * 退出页面。关闭请求。
     */
    public static void cancelNet() {
        if (call != null)
            try {
                call.cancel();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }
}
