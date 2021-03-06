package com.sergey.zhuravlev.auction.client.client;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.sergey.zhuravlev.auction.client.R;
import com.sergey.zhuravlev.auction.client.api.AccountEndpoint;
import com.sergey.zhuravlev.auction.client.api.AuthEndpoint;
import com.sergey.zhuravlev.auction.client.api.CategoryEndpoint;
import com.sergey.zhuravlev.auction.client.api.ImageEndpoint;
import com.sergey.zhuravlev.auction.client.api.LotEndpoint;
import com.sergey.zhuravlev.auction.client.api.UserEndpoint;
import com.sergey.zhuravlev.auction.client.dto.AccountRequestDto;
import com.sergey.zhuravlev.auction.client.dto.AccountResponseDto;
import com.sergey.zhuravlev.auction.client.dto.CategoryDto;
import com.sergey.zhuravlev.auction.client.dto.ErrorDto;
import com.sergey.zhuravlev.auction.client.dto.PageDto;
import com.sergey.zhuravlev.auction.client.dto.ResponseLotDto;
import com.sergey.zhuravlev.auction.client.dto.UserDto;
import com.sergey.zhuravlev.auction.client.dto.auth.AuthResponseDto;
import com.sergey.zhuravlev.auction.client.dto.auth.AuthorizationCodeDto;
import com.sergey.zhuravlev.auction.client.dto.auth.LoginRequestDto;
import com.sergey.zhuravlev.auction.client.dto.auth.SingUpRequestDto;
import com.sergey.zhuravlev.auction.client.dto.socket.NotificationRequestDto;
import com.sergey.zhuravlev.auction.client.dto.socket.NotificationResponseDto;
import com.sergey.zhuravlev.auction.client.exception.ErrorResponseException;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.reactivex.disposables.Disposable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;

public class Client {

    @Getter
    private static final Client instance = new Client();

    @Getter
    private static GoogleSignInClient googleSignInClient;

    public static final String CHANNEL_ID = "AUCTION_CHANNEL_ID";

    public static final String HOST = "192.168.100.44:8080";
    public static final String SERVER_URL = "http://" + HOST;
    public static final String SOCKET_URL = "ws://" + HOST + "/example-endpoint/websocket";

    @Setter
    @Getter
    private Context context;

    private ObjectMapper objectMapper;

    private String accessToken;

    @Getter
    private UserDto currentUser;
    private boolean isCurrentUserActual;

    private StompClient stompClient;

    private LotEndpoint lotEndpoints;
    private AuthEndpoint authEndpoint;
    private UserEndpoint userEndpoint;
    private ImageEndpoint imageEndpoint;
    private AccountEndpoint accountEndpoint;
    private CategoryEndpoint categoryEndpoint;

    private static final Callback EMPTY_CALLBACK = new Callback<Object>() {
        @Override
        public void onResponse(Call<Object> call, Response<Object> response) {
        }

        @Override
        public void onFailure(Call<Object> call, Throwable t) {
        }
    };

    private final AtomicInteger notificationIdsIncrementor = new AtomicInteger(0);

    public void init(Activity activity) {
        this.context = activity;

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.cookieJar(new DefaultCookieJar());
        builder.followRedirects(false);
        OkHttpClient httpClient = builder.build();

        objectMapper = new ObjectMapper();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(SERVER_URL)
                .client(httpClient)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build();

        lotEndpoints = retrofit.create(LotEndpoint.class);
        authEndpoint = retrofit.create(AuthEndpoint.class);
        userEndpoint = retrofit.create(UserEndpoint.class);
        imageEndpoint = retrofit.create(ImageEndpoint.class);
        accountEndpoint = retrofit.create(AccountEndpoint.class);
        categoryEndpoint = retrofit.create(CategoryEndpoint.class);

        // Google Sing-Up
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestServerAuthCode(context.getString(R.string.default_web_client_id))
                .requestProfile()
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(context, gso);
        googleSignInClient.silentSignIn().addOnCompleteListener(activity, task -> authenticate(task, EMPTY_CALLBACK));
    }

                            @Override
                            public void onFailure(Call<AuthResponseDto> call, Throwable t) {
                            }
                        });
                    }
                });
    }

    public boolean isAuthorized() {
        return accessToken != null;
    }

    public void authenticate(Task<GoogleSignInAccount> task, final Callback<AuthResponseDto> callback) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String accessToken = account.getServerAuthCode();
            authEndpoint
                    .authenticate("google", new AuthorizationCodeDto(accessToken))
                    .enqueue(new ErrorHandlerCallback<>(new Callback<AuthResponseDto>() {
                        @Override
                        public void onResponse(Call<AuthResponseDto> call, Response<AuthResponseDto> response) {
                            Client.this.accessToken = response.body().getAccessToken();
                            isCurrentUserActual = false;
                            callback.onResponse(call, response);
                        }

                        @Override
                        public void onFailure(Call<AuthResponseDto> call, Throwable t) {
                            callback.onFailure(call, t);
                        }
                    }));
        } catch (ApiException e) {
            Log.w("Auction.Login", "handleSignInResult:error", e);
        }
    }

    public void authenticate(String email, String password, final Callback<AuthResponseDto> callback) {
        authEndpoint
                .authenticate(new LoginRequestDto(email, password))
                .enqueue(new ErrorHandlerCallback<>(new Callback<AuthResponseDto>() {
                    @Override
                    public void onResponse(Call<AuthResponseDto> call, Response<AuthResponseDto> response) {
                        accessToken = response.body().getAccessToken();
                        isCurrentUserActual = false;
                        callback.onResponse(call, response);
                    }

                    @Override
                    public void onFailure(Call<AuthResponseDto> call, Throwable t) {
                        callback.onFailure(call, t);
                    }
                }));
    }

    public void register(SingUpRequestDto singUpRequestDto, final Callback<AuthResponseDto> callback) {
        authEndpoint
                .register(singUpRequestDto)
                .enqueue(new ErrorHandlerCallback<>(new Callback<UserDto>() {
                    @Override
                    public void onResponse(Call<UserDto> call, Response<UserDto> response) {
                        authenticate(singUpRequestDto.getEmail(), singUpRequestDto.getPassword(), callback);
                    }

                    @Override
                    public void onFailure(Call<UserDto> call, Throwable t) {
                        callback.onFailure((Call) call, t);
                    }
                }));
    }

    public void createUpdateAccount(AccountRequestDto accountRequestDto, final SimpleCallback<AccountResponseDto> callback) {
        accountEndpoint
                .createUpdate(getBearer(), accountRequestDto)
                .enqueue(new ErrorHandlerCallback<>(new Callback<AccountResponseDto>() {
                    @Override
                    public void onResponse(Call<AccountResponseDto> call, Response<AccountResponseDto> response) {
                        currentUser.setAccount(response.body());
                        callback.onResponse(response.body());
                    }

                    @Override
                    public void onFailure(Call<AccountResponseDto> call, Throwable t) {
                        callback.onFailure(t);
                    }
                }));
    }

    public void getCategoriesPage(SimpleCallback<PageDto<CategoryDto>> callback) {
        categoryEndpoint
                .page(getBearer())
                .enqueue(new ErrorHandlerSimpleCallback<>(callback));
    }

    public void categoriesPage(SimpleCallback<PageDto<CategoryDto>> callback, Integer page, Integer size) {
        categoryEndpoint
                .page(getBearer(), page, size)
                .enqueue(new ErrorHandlerSimpleCallback<>(callback));
    }

    public void uploadImage(Uri filePath, Callback<Void> callback) {
        try {
            InputStream fileInputStream = context.getContentResolver().openInputStream(filePath);
            if (fileInputStream != null) {
                byte[] file = IOUtils.toByteArray(fileInputStream);
                RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
                MultipartBody.Part body = MultipartBody.Part.createFormData("file", filePath.getPath().substring(filePath.getPath().lastIndexOf('/') + 1), requestFile);
                imageEndpoint.upload(getBearer(), body).enqueue(new ErrorHandlerCallback<>(callback));
            }
        } catch (IOException ignored) {
        }
    }

    public void getImage(String name, Callback<ResponseBody> callback) {
        imageEndpoint.download(name).enqueue(new ErrorHandlerCallback<>(callback));
    }

    public void getCurrentUser(final SimpleCallback<UserDto> callback) {
        if (!isCurrentUserActual) {
            userEndpoint.home(getBearer()).enqueue(new Callback<UserDto>() {
                @Override
                public void onResponse(@NonNull Call<UserDto> call, @NonNull Response<UserDto> response) {
                    currentUser = response.body();
                    isCurrentUserActual = true;
                    callback.onResponse(currentUser);
                }

                @Override
                public void onFailure(@NonNull Call<UserDto> call, @NonNull Throwable t) {
                    callback.onFailure(t);
                }
            });
        } else {
            callback.onResponse(currentUser);
        }
    }

    public void updateAccountPhoto(String photo, final SimpleCallback<AccountResponseDto> callback) {
        if (currentUser != null && currentUser.getAccount() != null) {
            final AccountResponseDto currentAccount = currentUser.getAccount();
            AccountRequestDto accountRequestDto = new AccountRequestDto(
                    currentAccount.getUsername(),
                    photo,
                    currentAccount.getFirstname(),
                    currentAccount.getLastname(),
                    currentAccount.getBio());
            accountEndpoint.createUpdate(getBearer(), accountRequestDto).enqueue(new ErrorHandlerSimpleCallback<>(new SimpleCallback<AccountResponseDto>() {
                @Override
                public void onResponse(AccountResponseDto response) {
                    currentUser.setAccount(response);
                    isCurrentUserActual = false;
                    callback.onResponse(response);
                }

                @Override
                public void onFailure(Throwable t) {
                    callback.onFailure(t);
                }
            }));
        }
    }

    public void getLotsPage(String category, String owner, String title, Integer page, Integer size, final Callback<PageDto<ResponseLotDto>> callback) {
        lotEndpoints.page(getBearer(), category, owner, title, page, size).enqueue(new ErrorHandlerCallback<>(callback));
    }

    private String getBearer() {
        if (accessToken == null) {
            return null;
        }
        return "Bearer " + accessToken;
    }

    @AllArgsConstructor
    class ErrorHandlerCallback<T> implements Callback<T> {

        private Callback<T> innerCallback;

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            if (successfulCode(response.code()) || redirectionCode(response.code())) {
                innerCallback.onResponse(call, response);
            } else {
                try {
                    if (response.errorBody() != null) {
                        String body = response.errorBody().string();
                        ErrorDto errorDto = objectMapper.readValue(body, ErrorDto.class);
                        innerCallback.onFailure(call, new ErrorResponseException(response.code(), errorDto));
                        return;
                    }
                    Log.d("Auction.Client", "Parse exception. Nullable body!");
                } catch (IOException e) {
                    Log.d("Auction.Client", "Parse exception!\n" + Log.getStackTraceString(e));
                }
                innerCallback.onFailure(call, new ErrorResponseException(response.code()));
            }
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            innerCallback.onFailure(call, t);
        }

        private boolean successfulCode(int code) {
            return code >= 200 && code < 300;
        }

        private boolean redirectionCode(int code) {
            return code >= 300 && code < 400;
        }
    }

    @AllArgsConstructor
    class ErrorHandlerSimpleCallback<T> implements Callback<T> {

        private SimpleCallback<T> innerCallback;

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            if (successfulCode(response.code()) || redirectionCode(response.code())) {
                innerCallback.onResponse(response.body());
            } else {
                try {
                    if (response.errorBody() != null) {
                        String body = response.errorBody().string();
                        ErrorDto errorDto = objectMapper.readValue(body, ErrorDto.class);
                        innerCallback.onFailure(new ErrorResponseException(response.code(), errorDto));
                        return;
                    }
                } catch (IOException ignored) {
                }
                innerCallback.onFailure(new ErrorResponseException(response.code()));
            }
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            innerCallback.onFailure(t);
        }

        private boolean successfulCode(int code) {
            return code >= 200 && code < 300;
        }

        private boolean redirectionCode(int code) {
            return code >= 300 && code < 400;
        }
    }

}
