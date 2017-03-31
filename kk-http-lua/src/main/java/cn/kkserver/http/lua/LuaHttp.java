package cn.kkserver.http.lua;

import android.os.Handler;
import android.util.Log;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.TreeMap;
import cn.kkserver.core.IGetter;
import cn.kkserver.core.Json;
import cn.kkserver.core.Value;
import cn.kkserver.lua.LuaFunction;
import cn.kkserver.lua.LuaState;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by zhanghailong on 2017/3/7.
 */

public class LuaHttp implements IGetter {

    public final static String TAG = "kk";
    private final static int BUFFER_SIZE = 204800;

    private final OkHttpClient _client ;

    private static class LuaCallback implements Callback {

        private final LuaState _L;
        private int _onload;
        private int _onfail;
        private final String _type;
        private final Handler _handler;

        public LuaCallback(LuaState L,int onload,int onfail,String type) {
            _L = L;
            _onload = onload;
            _onfail = onfail;
            _type = type;
            _handler = new Handler();
        }

        @Override
        protected void finalize() throws Throwable {

            if(_L != null) {

                if(_onload != 0) {
                    final int ref = _onload;
                    final LuaState L = _L;

                    _handler.post(new Runnable() {
                        @Override
                        public void run() {
                            L.unref(ref);
                        }
                    });

                }

                if(_onfail != 0) {

                    final int ref = _onfail;
                    final LuaState L = _L;

                    _handler.post(new Runnable() {
                        @Override
                        public void run() {
                            L.unref(ref);
                        }
                    });

                }
            }

            super.finalize();
        }

        @Override
        public void onFailure(Call call, final IOException e) {

            final int onfail = _onfail;
            final int onload = _onload;
            final LuaState L = _L;

            _handler.post(new Runnable() {
                @Override
                public void run() {

                    if(onfail != 0) {

                        L.getref(onfail);

                        if(L.type(-1) == LuaState.LUA_TFUNCTION) {

                            L.pushstring(e.getMessage());

                            if(_L.pcall(1,0) != 0) {
                                String errmsg = _L.tostring(-1);
                                L.pop(1);
                                Log.d("kk",errmsg);
                            }

                        } else {
                            L.pop(1);
                        }
                    }

                    if(onload != 0) {
                        L.unref(onload);
                    }

                    if(onfail != 0) {
                        L.unref(onfail);
                    }

                }
            });

            _onload = 0;
            _onfail = 0;

        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {

            if(! response.isSuccessful()) {
                onFailure(call,new IOException("数据错误"));
                return;
            }

            final Map<String,Object> headers = new TreeMap<String,Object>();

            {
                Headers vs = response.headers();

                for(int i=0;i<vs.size();i++) {
                    headers.put(vs.name(i),vs.value(i));
                }
            }

            String text = response.body().string();
            Object body = text;

            if("json".equals(_type)) {
                try {
                    body = Json.decodeString(text);
                } catch (JSONException e) {
                    onFailure(call,new IOException(e));
                    return;
                }
            } else if("file".equals(_type)) {
                try {

                    File file = File.createTempFile("kk", "");
                    FileOutputStream out = new FileOutputStream(file);
                    InputStream in = response.body().byteStream();

                    try {

                        byte[] data = new byte[BUFFER_SIZE];
                        int length;

                        while((length = in.read(data)) > 0) {
                            out.write(data,0,length);
                        }

                        out.flush();
                    }
                    finally {

                        try {
                            in.close();
                        } finally {
                            out.close();
                        }

                    }

                    body = file.getAbsolutePath();

                }
                catch (IOException e) {
                    onFailure(call,new IOException(e));
                    return;
                }
            }

            {
                final int onfail = _onfail;
                final int onload = _onload;
                final LuaState L = _L;
                final Object data = body;

                _handler.post(new Runnable() {
                    @Override
                    public void run() {

                        if (onload != 0) {

                            L.getref(onload);

                            if (L.type(-1) == LuaState.LUA_TFUNCTION) {

                                L.pushValue(data);
                                L.pushValue(headers);

                                if (_L.pcall(2, 0) != 0) {
                                    String errmsg = _L.tostring(-1);
                                    L.pop(1);
                                    Log.d("kk", errmsg);
                                }

                            } else {
                                L.pop(1);
                            }
                        }

                        if (onload != 0) {
                            L.unref(onload);
                        }

                        if (onfail != 0) {
                            L.unref(onfail);
                        }

                    }
                });

                _onload = 0;
                _onfail = 0;
            }

        }
    }

    private static final LuaFunction _funcSend = new LuaFunction() {
        @Override
        public int invoke(LuaState luaState) {

            int top = luaState.gettop();

            if(top > 1 && luaState.type( - top ) == LuaState.LUA_TOBJECT && luaState.type( - top + 1) == LuaState.LUA_TTABLE) {

                Object object = luaState.toobject(- top);

                Object v = null;

                if(object instanceof LuaHttp) {

                    OkHttpClient client = ((LuaHttp) object)._client;

                    Request.Builder build = new Request.Builder();

                    luaState.pushvalue(- top + 1);

                    luaState.pushnil();

                    int onload = 0,onfail = 0;
                    String method = "GET";
                    String contentType = "application/x-www-form-urlencoded";
                    String url = "";
                    String type = "json";

                    while(luaState.next(-2) != 0) {

                        if(luaState.type(-2) == LuaState.LUA_TSTRING) {
                            String name = luaState.tostring(-2);
                            if("onload".equals(name)) {
                                if(luaState.type(-1) == LuaState.LUA_TFUNCTION) {
                                    onload = luaState.ref();
                                    continue;
                                }
                            }
                            else if("onfail".equals(name)) {
                                if(luaState.type(-1) == LuaState.LUA_TFUNCTION) {
                                    onfail = luaState.ref();
                                    continue;
                                }
                            }
                            else if("url".equals(name)) {
                                if(luaState.type(-1) == LuaState.LUA_TSTRING) {
                                    url = luaState.tostring(-1);
                                }
                            }
                            else if("method".equals(name)) {
                                if(luaState.type(-1) == LuaState.LUA_TSTRING) {
                                    method = luaState.tostring(-1);
                                }
                            }
                            else if("type".equals(name)) {
                                if(luaState.type(-1) == LuaState.LUA_TSTRING) {
                                    type = luaState.tostring(-1);
                                }
                            }
                            else if("headers".equals(name)) {

                                if(luaState.type(-1) == LuaState.LUA_TTABLE) {

                                    luaState.pushnil();

                                    while(luaState.next(-2) != 0) {

                                        if(luaState.type(-2) == LuaState.LUA_TSTRING && luaState.type(-1) == LuaState.LUA_TSTRING) {
                                            String key = luaState.tostring(-2);
                                            String value = luaState.tostring(-1);
                                            build.addHeader(key,value);
                                            if("Content-Type".equals(key)) {
                                                contentType = value.split(";")[0];
                                            }
                                        }

                                        luaState.pop(1);
                                    }
                                }

                            }
                        }

                        luaState.pop(1);
                    }

                    if("POST".equals(method)) {

                        build.url(url);

                        if("application/x-www-form-urlencoded".equals(contentType)) {

                            luaState.pushstring("data");
                            luaState.rawget(-2);

                            if(luaState.type(-1) == LuaState.LUA_TTABLE) {

                                FormBody.Builder body  = new FormBody.Builder();

                                luaState.pushnil();

                                while(luaState.next(-2) != 0) {

                                    if(luaState.type(-2) == LuaState.LUA_TSTRING ) {
                                        String key = luaState.tostring(-2);
                                        String value = Value.stringValue(luaState.toValue(-1),"");
                                        body.add(key,value);
                                    }

                                    luaState.pop(1);
                                }

                                build.method(method,body.build());

                            }

                            luaState.pop(1);

                        } else {

                            luaState.pushstring("data");
                            luaState.rawget(-2);

                            if(luaState.type(-1) == LuaState.LUA_TSTRING) {
                                build.method(method,RequestBody.create(MediaType.parse(contentType),luaState.tostring(-1)));
                            }

                            luaState.pop(1);

                        }

                        Log.d(TAG,url);

                    } else {

                        StringBuilder sb = new StringBuilder();
                        sb.append(url);

                        int i = 0;

                        if(url.endsWith("?")) {

                        } else {
                            i = url.indexOf("?");
                            if(i == -1) {
                                i = 0;
                                sb.append("?");
                            }
                            else {
                                i = 1;
                            }
                        }

                        luaState.pushstring("data");
                        luaState.rawget(-2);

                        if(luaState.type(-1) == LuaState.LUA_TTABLE) {

                            luaState.pushnil();

                            while(luaState.next(-2) != 0) {

                                if(luaState.type(-2) == LuaState.LUA_TSTRING ) {
                                    String key = luaState.tostring(-2);
                                    String value = Value.stringValue(luaState.toValue(-1),"");

                                    if(i != 0) {
                                        sb.append("&");
                                    }
                                    try {
                                        sb.append(key).append("=").append(URLEncoder.encode(value,"utf-8"));
                                    } catch (UnsupportedEncodingException e) {
                                    }
                                    i ++;
                                }

                                luaState.pop(1);
                            }


                        }

                        luaState.pop(1);

                        build.url(sb.toString());

                        Log.d(TAG,sb.toString());
                    }

                    luaState.pop(1);

                    Call call = client.newCall(build.build());

                    call.enqueue(new LuaCallback(luaState,onload,onfail,type));

                    luaState.pushobject(call);
                    return 1;

                }


                luaState.pushnil();

                return 1;
            }

            return 0;
        }
    };

    private static final LuaFunction _funcCancel = new LuaFunction() {
        @Override
        public int invoke(LuaState luaState) {

            int top = luaState.gettop();

            if(top > 1 && luaState.type( - top ) == LuaState.LUA_TOBJECT && luaState.type( - top + 1) == LuaState.LUA_TOBJECT) {

                Object object = luaState.toobject(- top);

                Object call = luaState.toobject(- top + 1);

                if(object instanceof LuaHttp) {

                    if(call instanceof Call) {
                        ((Call) call).cancel();
                    }
                }

                return 0;
            }

            return 0;
        }
    };


    public LuaHttp(OkHttpClient client) {
        _client = client;
    }

    public LuaHttp() {
        _client = new OkHttpClient.Builder().build();
    }

    @Override
    public Object get(String s) {
        if("send".equals(s)) {
            return _funcSend;
        }
        else if("cancel".equals(s)) {
            return _funcCancel;
        }
        return null;
    }
}
