package com.myshows.studentapp.rest.interceptors;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;

import com.myshows.studentapp.Application;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ExponentialBackoffInterceptor implements Interceptor {

    public static final int BAD_REQUEST = 400;
    public static final int REQUEST_TIMEOUT = 408;
    public static final int TOO_MANY_REQUESTS = 429;
    public static final int INTERNAL_SERVER_ERROR = 500;
    public static final int SERVICE_UNAVAILABLE = 503;
    public static final int GATEWAY_TIMEOUT = 504;

    private int[] errorCodes = {
            BAD_REQUEST,
            REQUEST_TIMEOUT,
            TOO_MANY_REQUESTS,
            INTERNAL_SERVER_ERROR,
            SERVICE_UNAVAILABLE,
            GATEWAY_TIMEOUT
    };

    private int growthFactor = 10;
    private long minTimeInMillis = TimeUnit.SECONDS.toMillis(10);
    private long maxTimeInMillis = TimeUnit.SECONDS.toMillis(60);
    private Function function = new LinearFunction();

    private int retryCount;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        int responseCode = response.code();
        long delay = function.getDelay(retryCount++);

        if (delay != maxTimeInMillis + 1) {
            for (int errorCode : errorCodes) {
                if (errorCode == responseCode) {
                    Request newRequest = chain.request();
                    newRequest = newRequest.newBuilder()
                            .addHeader("Cookie", Application.getCookies().toString())
                            .build();
                    try {
                        wait(delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    response = chain.proceed(newRequest);
                    break;
                }
            }
        }
        return response;
    }

    public abstract class Function {
        private long getDelay(int retryCount) {
            long result = minTimeInMillis;
            for (int i = 0; i < retryCount; i++) {
                result += growthFactor;
                if (result > maxTimeInMillis) return maxTimeInMillis + 1;
            }
            return result;
        }

        abstract long functionMath(long result,
                                   int retryCount,
                                   long minTime, long maxTime,
                                   int growthFactor);
    }

    public class ExponentialFunction extends Function {
        @Override
        long functionMath(long result,
                          int retryCount,
                          long minTime, long maxTime,
                          int growthFactor) {
            return result * growthFactor;
        }
    }

    public class LinearFunction extends Function {
        @Override
        long functionMath(long result,
                          int retryCount,
                          long minTime, long maxTime,
                          int growthFactor) {
            return result + growthFactor;
        }
    }

    public static final class Builder {
        private ExponentialBackoffInterceptor interceptor;

        public Builder() {
            interceptor = new ExponentialBackoffInterceptor();
        }

        public Builder setGrowthFactor(@IntRange(from = 1) int growthFactor) {
            interceptor.growthFactor = growthFactor;
            return this;
        }

        public Builder setErrorCodes(@IntRange(from = 400, to = 599) int... errorCodes) {
            interceptor.errorCodes = errorCodes;
            return this;
        }

        public Builder setMinTime(@IntRange(from = 0) long minTimeInMillis) {
            interceptor.minTimeInMillis = minTimeInMillis;
            return this;
        }

        public Builder setMaxTime(@IntRange(from = 1) long maxTimeInMillis) {
            interceptor.maxTimeInMillis = maxTimeInMillis;
            return this;
        }

        public Builder setFunction(@NonNull Function function) {
            interceptor.function = function;
            return this;
        }

        public ExponentialBackoffInterceptor build() {
            return interceptor;
        }

    }

}
