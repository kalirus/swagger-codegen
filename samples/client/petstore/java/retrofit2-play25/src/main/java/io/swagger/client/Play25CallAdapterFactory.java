package io.swagger.client;

import java.util.concurrent.CompletionStage;
import retrofit2.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Creates {@link CallAdapter} instances that convert {@link Call} into {@link java.util.concurrent.CompletionStage}
 */
public class Play25CallAdapterFactory extends CallAdapter.Factory {
    
    private Function<RuntimeException, RuntimeException> exceptionConverter = Function.identity();

    public Play25CallAdapterFactory() {
    }

    public Play25CallAdapterFactory(
            Function<RuntimeException, RuntimeException> exceptionConverter) {
        this.exceptionConverter = exceptionConverter;
    }

    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (!(returnType instanceof ParameterizedType)) {
            return null;
        }

        ParameterizedType type = (ParameterizedType) returnType;
        if (type.getRawType() != CompletionStage.class) {
            return null;
        }

        return createAdapter((ParameterizedType) returnType);
    }

    private Type getTypeParam(ParameterizedType type) {
        Type[] types = type.getActualTypeArguments();
        if (types.length != 1) {
            throw new IllegalStateException("Must be exactly one type parameter");
        }

        Type paramType = types[0];
        
        Class<?> rawTypeParam = getRawType(paramType);
        if (rawTypeParam == Response.class) {
            if (!(paramType instanceof ParameterizedType)) {
                throw new IllegalStateException("Response must be parameterized"
                        + " as Response<Foo>");
            }
            return ((ParameterizedType) paramType).getActualTypeArguments()[0];
        }

        throw new IllegalStateException("Return type must be defined as "
                + " as CompletionStage<Response<Foo>>");
    }

    private CallAdapter<?, CompletionStage<?>> createAdapter(ParameterizedType returnType) {
        Type parameterType = getTypeParam(returnType);
        return new ValueAdapter(parameterType, exceptionConverter);
    }

    /**
     * Adpater that coverts values returned by API interface into CompletionStage
     */
    static final class ValueAdapter<R> implements CallAdapter<R, CompletionStage<Response<R>>> {

        private final Type responseType;
        private Function<RuntimeException, RuntimeException> exceptionConverter;

        ValueAdapter(Type responseType,
                     Function<RuntimeException, RuntimeException> exceptionConverter) {
            this.responseType = responseType;
            this.exceptionConverter = exceptionConverter;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public CompletionStage<Response<R>> adapt(final Call<R> call) {
            final CompletableFuture<Response<R>> promise = new CompletableFuture();

            call.enqueue(new Callback<R>() {

                @Override
                public void onResponse(Call<R> call, Response<R> response) {
                    if (response.isSuccessful()) {
                        promise.complete(response);
                    } else {
                        promise.completeExceptionally(exceptionConverter.apply(new HttpException(response)));
                    }
                }

                @Override
                public void onFailure(Call<R> call, Throwable t) {
                    promise.completeExceptionally(t);
                }

            });

            return promise;
        }
    }
}

