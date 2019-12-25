/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import com.google.android.gnd.rx.ValueOrError;
import io.reactivex.Flowable;
import java8.util.Optional;
import javax.annotation.Nullable;
import org.reactivestreams.Publisher;

/**
 * Wraps the state of an entity that can be loaded asynchronously. Based on {@code Resource} in
 * Android Guide to App Architecture: https://developer.android.com/jetpack/docs/guide#addendum
 *
 * @param <T> the type of data payload the resource contains.
 */
public class Loadable<T> extends ValueOrError<T> {
  private final LoadState state;

  public enum LoadState {
    NOT_LOADED,
    LOADING,
    LOADED,
    NOT_FOUND,
    ERROR
  }

  private Loadable(LoadState state, @Nullable T data, Throwable error) {
    super(data, error);
    this.state = state;
  }

  public static <T> Loadable<T> notLoaded() {
    return new Loadable<>(LoadState.NOT_LOADED, null, null);
  }

  public static <T> Loadable<T> loading() {
    return new Loadable<>(LoadState.LOADING, null, null);
  }

  public static <T> Loadable<T> loaded(T data) {
    return new Loadable<>(LoadState.LOADED, data, null);
  }

  public static <T> Loadable<T> error(Throwable t) {
    return new Loadable<>(LoadState.ERROR, null, t);
  }

  public LoadState getState() {
    return state;
  }

  public boolean isLoaded() {
    return state == LoadState.LOADED;
  }

  // TODO: Move this into new extended LiveData class (LiveResource?).
  @NonNull
  public static <T> Optional<T> getData(LiveData<Loadable<T>> liveData) {
    return liveData.getValue() == null ? Optional.empty() : liveData.getValue().value();
  }

  /**
   * Modifies the provided stream to emit values instead of {@link Loadable} only when a value is
   * loaded (i.e., omitting intermediate loading and error states).
   */
  public static <V> Publisher<V> values(Flowable<Loadable<V>> persistableStream) {
    return persistableStream.map(Loadable::value).filter(Optional::isPresent).map(Optional::get);
  }

  /**
   * Returns a {@link Flowable} that first emits the LOADING, then maps values emitted from the
   * source stream to {@code Loadable}s with a LOADED {@code Loadable}. Errors in the provided
   * stream are handled and wrapped in a {@code Loadable} with state ERROR. The returned stream
   * itself should never fail with an error.
   *
   * @param source the stream responsible for loading values.
   * @param <T> the type of entity being loaded
   */
  public static <T> Flowable<Loadable<T>> loadingOnceAndWrap(Flowable<T> source) {
    return source
        .map(Loadable::loaded)
        .onErrorReturn(Loadable::error)
        .startWith(Loadable.loading());
  }
}
