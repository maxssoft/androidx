/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.paging

import androidx.annotation.IntRange
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.paging.LoadType.REFRESH
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Helper class for mapping a [PagingData] into a
 * [RecyclerView.Adapter][androidx.recyclerview.widget.RecyclerView.Adapter].
 *
 * For simplicity, [PagingDataAdapter] can often be used in place of this class.
 * [AsyncPagingDataDiffer] is exposed for complex cases, and where overriding [PagingDataAdapter] to
 * support paging isn't convenient.
 */
class AsyncPagingDataDiffer<T : Any>
/**
 * Construct an [AsyncPagingDataDiffer].
 *
 * @param diffCallback Callback for calculating the diff between two non-disjoint lists on
 * [REFRESH]. Used as a fallback for item-level diffing when Paging is unable to find a faster
 * path for generating the UI events required to display the new list.
 * @param updateCallback [ListUpdateCallback] which receives UI events dispatched by this
 * [AsyncPagingDataDiffer] as items are loaded.
 * @param mainDispatcher [CoroutineContext] where UI events are dispatched. Typically, this should
 * be [Dispatchers.Main].
 * @param workerDispatcher [CoroutineContext] where the work to generate UI events is dispatched,
 * for example when diffing lists on [REFRESH]. Typically, this should dispatch on a background
 * thread; [Dispatchers.Default] by default.
 */
@JvmOverloads
constructor(
    private val diffCallback: DiffUtil.ItemCallback<T>,
    @Suppress("ListenerLast") // have to suppress for each, due to optional args
    private val updateCallback: ListUpdateCallback,
    @Suppress("ListenerLast") // have to suppress for each, due to optional args
    private val mainDispatcher: CoroutineContext = Dispatchers.Main,
    @Suppress("ListenerLast") // have to suppress for each, due to optional args
    private val workerDispatcher: CoroutineContext = Dispatchers.Default,
) {
    /**
     * Construct an [AsyncPagingDataDiffer].
     *
     * @param diffCallback Callback for calculating the diff between two non-disjoint lists on
     * [REFRESH]. Used as a fallback for item-level diffing when Paging is unable to find a faster
     * path for generating the UI events required to display the new list.
     * @param updateCallback [ListUpdateCallback] which receives UI events dispatched by this
     * [AsyncPagingDataDiffer] as items are loaded.
     * @param mainDispatcher [CoroutineDispatcher] where UI events are dispatched. Typically,
     * this should be [Dispatchers.Main].
     */
    @Deprecated(
        message = "Superseded by constructors which accept CoroutineContext",
        level = DeprecationLevel.HIDDEN
    )
    // Only for binary compatibility; cannot apply @JvmOverloads as the function signature would
    // conflict with the primary constructor.
    @Suppress("MissingJvmstatic")
    constructor(
        diffCallback: DiffUtil.ItemCallback<T>,
        @Suppress("ListenerLast") // have to suppress for each, due to optional args
        updateCallback: ListUpdateCallback,
        @Suppress("ListenerLast") // have to suppress for each, due to optional args
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    ) : this(
        diffCallback = diffCallback,
        updateCallback = updateCallback,
        mainDispatcher = mainDispatcher,
        workerDispatcher = Dispatchers.Default
    )

    /**
     * Construct an [AsyncPagingDataDiffer].
     *
     * @param diffCallback Callback for calculating the diff between two non-disjoint lists on
     * [REFRESH]. Used as a fallback for item-level diffing when Paging is unable to find a faster
     * path for generating the UI events required to display the new list.
     * @param updateCallback [ListUpdateCallback] which receives UI events dispatched by this
     * [AsyncPagingDataDiffer] as items are loaded.
     * @param mainDispatcher [CoroutineDispatcher] where UI events are dispatched. Typically,
     * this should be [Dispatchers.Main].
     * @param workerDispatcher [CoroutineDispatcher] where the work to generate UI events is
     * dispatched, for example when diffing lists on [REFRESH]. Typically, this should dispatch on a
     * background thread; [Dispatchers.Default] by default.
     */
    @Deprecated(
        message = "Superseded by constructors which accept CoroutineContext",
        level = DeprecationLevel.HIDDEN
    )
    // Only for binary compatibility; cannot apply @JvmOverloads as the function signature would
    // conflict with the primary constructor.
    @Suppress("MissingJvmstatic")
    constructor(
        diffCallback: DiffUtil.ItemCallback<T>,
        @Suppress("ListenerLast") // have to suppress for each, due to optional args
        updateCallback: ListUpdateCallback,
        @Suppress("ListenerLast") // have to suppress for each, due to optional args
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
        @Suppress("ListenerLast") // have to suppress for each, due to optional args
        workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this(
        diffCallback = diffCallback,
        updateCallback = updateCallback,
        mainDispatcher = mainDispatcher,
        workerDispatcher = workerDispatcher
    )

    @Suppress("MemberVisibilityCanBePrivate") // synthetic access
    internal val differCallback = object : DifferCallback {
        override fun onInserted(position: Int, count: Int) {
            // Ignore if count == 0 as it makes this event a no-op.
            if (count > 0) {
                updateCallback.onInserted(position, count)
            }
        }

        override fun onRemoved(position: Int, count: Int) {
            // Ignore if count == 0 as it makes this event a no-op.
            if (count > 0) {
                updateCallback.onRemoved(position, count)
            }
        }

        override fun onChanged(position: Int, count: Int) {
            // Ignore if count == 0 as it makes this event a no-op.
            if (count > 0) {
                // NOTE: pass a null payload to convey null -> item, or item -> null
                updateCallback.onChanged(position, count, null)
            }
        }
    }

    /** True if we're currently executing [getItem] */
    @Suppress("MemberVisibilityCanBePrivate") // synthetic access
    internal var inGetItem: Boolean = false

    internal val presenter = object : PagingDataPresenter<T>(differCallback, mainDispatcher) {
        // TODO("To be removed when all PageEvent types have moved to presentPagingDataEvent")
        override suspend fun presentNewList(
            previousList: NullPaddedList<T>,
            newList: NullPaddedList<T>,
            lastAccessedIndex: Int,
            onListPresentable: () -> Unit,
        ) {
            when {
                // fast path for no items -> some items
                previousList.size == 0 -> {
                    onListPresentable()
                    differCallback.onInserted(0, newList.size)
                }
                // fast path for some items -> no items
                newList.size == 0 -> {
                    onListPresentable()
                    differCallback.onRemoved(0, previousList.size)
                }

                else -> {
                    val diffResult = withContext(workerDispatcher) {
                        previousList.computeDiff(newList, diffCallback)
                    }
                    onListPresentable()
                    previousList.dispatchDiff(updateCallback, newList, diffResult)
                }
            }
        }

        /**
         * Insert the event's page to the storage, and dispatch associated callbacks for
         * change (placeholder becomes real item) or insert (real item is appended).
         *
         * For each insert (or removal) there are three potential events:
         *
         * 1) change
         *     this covers any placeholder/item conversions, and is done first
         *
         * 2) item insert/remove
         *     this covers any remaining items that are inserted/removed, but aren't swapping with
         *     placeholders
         *
         * 3) placeholder insert/remove
         *     after the above, placeholder count can be wrong for a number of reasons - approximate
         *     counting or filtering are the most common. In either case, we adjust placeholders at
         *     the far end of the list, so that they don't trigger animations near the user.
         */
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            when (event) {
                is PagingDataEvent.Prepend -> event.apply {
                    val insertSize = inserted.size

                    val placeholdersChangedCount =
                        minOf(oldPlaceholdersBefore, insertSize)
                    val placeholdersChangedPos = oldPlaceholdersBefore - placeholdersChangedCount
                    val itemsInsertedCount = insertSize - placeholdersChangedCount
                    val itemsInsertedPos = 0

                    // ... then trigger callbacks, so callbacks won't see inconsistent state
                    if (placeholdersChangedCount > 0) {
                        updateCallback.onChanged(
                            placeholdersChangedPos, placeholdersChangedCount, null
                        )
                    }
                    if (itemsInsertedCount > 0) {
                        updateCallback.onInserted(itemsInsertedPos, itemsInsertedCount)
                    }
                    val placeholderInsertedCount =
                        newPlaceholdersBefore - oldPlaceholdersBefore + placeholdersChangedCount
                    if (placeholderInsertedCount > 0) {
                        updateCallback.onInserted(0, placeholderInsertedCount)
                    } else if (placeholderInsertedCount < 0) {
                        updateCallback.onRemoved(0, -placeholderInsertedCount)
                    }
                }
                is PagingDataEvent.Append -> event.apply {
                    val insertSize = inserted.size
                    val placeholdersChangedCount = minOf(oldPlaceholdersAfter, insertSize)
                    val placeholdersChangedPos = startIndex
                    val itemsInsertedCount = insertSize - placeholdersChangedCount
                    val itemsInsertedPos = placeholdersChangedPos + placeholdersChangedCount

                    if (placeholdersChangedCount > 0) {
                        updateCallback.onChanged(
                            placeholdersChangedPos, placeholdersChangedCount, null
                        )
                    }
                    if (itemsInsertedCount > 0) {
                        updateCallback.onInserted(itemsInsertedPos, itemsInsertedCount)
                    }
                    val placeholderInsertedCount =
                        newPlaceholdersAfter - oldPlaceholdersAfter + placeholdersChangedCount
                    val newTotalSize = startIndex + insertSize + newPlaceholdersAfter
                    if (placeholderInsertedCount > 0) {
                        updateCallback.onInserted(
                            newTotalSize - placeholderInsertedCount,
                            placeholderInsertedCount
                        )
                    } else if (placeholderInsertedCount < 0) {
                        updateCallback.onRemoved(newTotalSize, -placeholderInsertedCount)
                    }
                }
                else -> {
                    // to implement
                }
            }
        }

        /**
         * Return if [getItem] is running to post any data modifications.
         *
         * This must be done because RecyclerView can't be modified during an onBind, when
         * [getItem] is generally called.
         */
        override fun postEvents(): Boolean {
            return inGetItem
        }
    }

    private val submitDataId = AtomicInteger(0)

    /**
     * Present a [PagingData] until it is invalidated by a call to [refresh] or
     * [PagingSource.invalidate].
     *
     * This method is typically used when collecting from a [Flow][kotlinx.coroutines.flow.Flow]
     * produced by [Pager]. For RxJava or LiveData support, use the non-suspending overload of
     * [submitData], which accepts a [Lifecycle].
     *
     * Note: This method suspends while it is actively presenting page loads from a [PagingData],
     * until the [PagingData] is invalidated. Although cancellation will propagate to this call
     * automatically, collecting from a [Pager.flow] with the intention of presenting the most
     * up-to-date representation of your backing dataset should typically be done using
     * [collectLatest][kotlinx.coroutines.flow.collectLatest].
     *
     * @see [Pager]
     */
    suspend fun submitData(pagingData: PagingData<T>) {
        submitDataId.incrementAndGet()
        presenter.collectFrom(pagingData)
    }

    /**
     * Present a [PagingData] until it is either invalidated or another call to [submitData] is
     * made.
     *
     * This method is typically used when observing a RxJava or LiveData stream produced by [Pager].
     * For [Flow][kotlinx.coroutines.flow.Flow] support, use the suspending overload of
     * [submitData], which automates cancellation via
     * [CoroutineScope][kotlinx.coroutines.CoroutineScope] instead of relying of [Lifecycle].
     *
     * @see submitData
     * @see [Pager]
     */
    fun submitData(lifecycle: Lifecycle, pagingData: PagingData<T>) {
        val id = submitDataId.incrementAndGet()
        lifecycle.coroutineScope.launch {
            // Check id when this job runs to ensure the last synchronous call submitData always
            // wins.
            if (submitDataId.get() == id) {
                presenter.collectFrom(pagingData)
            }
        }
    }

    /**
     * Retry any failed load requests that would result in a [LoadState.Error] update to this
     * [AsyncPagingDataDiffer].
     *
     * Unlike [refresh], this does not invalidate [PagingSource], it only retries failed loads
     * within the same generation of [PagingData].
     *
     * [LoadState.Error] can be generated from two types of load requests:
     *  * [PagingSource.load] returning [PagingSource.LoadResult.Error]
     *  * [RemoteMediator.load] returning [RemoteMediator.MediatorResult.Error]
     */
    fun retry() {
        presenter.retry()
    }

    /**
     * Refresh the data presented by this [AsyncPagingDataDiffer].
     *
     * [refresh] triggers the creation of a new [PagingData] with a new instance of [PagingSource]
     * to represent an updated snapshot of the backing dataset. If a [RemoteMediator] is set,
     * calling [refresh] will also trigger a call to [RemoteMediator.load] with [LoadType] [REFRESH]
     * to allow [RemoteMediator] to check for updates to the dataset backing [PagingSource].
     *
     * Note: This API is intended for UI-driven refresh signals, such as swipe-to-refresh.
     * Invalidation due repository-layer signals, such as DB-updates, should instead use
     * [PagingSource.invalidate].
     *
     * @see PagingSource.invalidate
     *
     * @sample androidx.paging.samples.refreshSample
     */
    fun refresh() {
        presenter.refresh()
    }

    /**
     * Get the item from the current PagedList at the specified index.
     *
     * Note that this operates on both loaded items and null padding within the PagedList.
     *
     * @param index Index of item to get, must be >= 0, and < [itemCount]
     * @return The item, or `null`, if a `null` placeholder is at the specified position.
     */
    @MainThread
    fun getItem(@IntRange(from = 0) index: Int): T? {
        try {
            inGetItem = true
            return presenter[index]
        } finally {
            inGetItem = false
        }
    }

    /**
     * Returns the presented item at the specified position, without notifying Paging of the item
     * access that would normally trigger page loads.
     *
     * @param index Index of the presented item to return, including placeholders.
     * @return The presented item at position [index], `null` if it is a placeholder
     */
    @MainThread
    fun peek(@IntRange(from = 0) index: Int): T? {
        return presenter.peek(index)
    }

    /**
     * Returns a new [ItemSnapshotList] representing the currently presented items, including any
     * placeholders if they are enabled.
     */
    fun snapshot(): ItemSnapshotList<T> = presenter.snapshot()

    /**
     * Get the number of items currently presented by this Differ. This value can be directly
     * returned to [androidx.recyclerview.widget.RecyclerView.Adapter.getItemCount].
     *
     * @return Number of items being presented, including placeholders.
     */
    val itemCount: Int
        get() = presenter.size

    /**
     * A hot [Flow] of [CombinedLoadStates] that emits a snapshot whenever the loading state of the
     * current [PagingData] changes.
     *
     * This flow is conflated, so it buffers the last update to [CombinedLoadStates] and
     * immediately delivers the current load states on collection.
     *
     * @sample androidx.paging.samples.loadStateFlowSample
     */
    val loadStateFlow: Flow<CombinedLoadStates> = presenter.loadStateFlow.filterNotNull()

    /**
     * A hot [Flow] that emits after the pages presented to the UI are updated, even if the
     * actual items presented don't change.
     *
     * An update is triggered from one of the following:
     *   * [submitData] is called and initial load completes, regardless of any differences in
     *     the loaded data
     *   * A [Page][androidx.paging.PagingSource.LoadResult.Page] is inserted
     *   * A [Page][androidx.paging.PagingSource.LoadResult.Page] is dropped
     *
     * Note: This is a [SharedFlow][kotlinx.coroutines.flow.SharedFlow] configured to replay
     * 0 items with a buffer of size 64. If a collector lags behind page updates, it may
     * trigger multiple times for each intermediate update that was presented while your collector
     * was still working. To avoid this behavior, you can
     * [conflate][kotlinx.coroutines.flow.conflate] this [Flow] so that you only receive the latest
     * update, which is useful in cases where you are simply updating UI and don't care about
     * tracking the exact number of page updates.
     */
    val onPagesUpdatedFlow: Flow<Unit> = presenter.onPagesUpdatedFlow

    /**
     * Add a listener which triggers after the pages presented to the UI are updated, even if the
     * actual items presented don't change.
     *
     * An update is triggered from one of the following:
     *   * [submitData] is called and initial load completes, regardless of any differences in
     *     the loaded data
     *   * A [Page][androidx.paging.PagingSource.LoadResult.Page] is inserted
     *   * A [Page][androidx.paging.PagingSource.LoadResult.Page] is dropped
     *
     * @param listener called after pages presented are updated.
     *
     * @see removeOnPagesUpdatedListener
     */
    fun addOnPagesUpdatedListener(listener: () -> Unit) {
        presenter.addOnPagesUpdatedListener(listener)
    }

    /**
     * Remove a previously registered listener for new [PagingData] generations completing
     * initial load and presenting to the UI.
     *
     * @param listener Previously registered listener.
     *
     * @see addOnPagesUpdatedListener
     */
    fun removeOnPagesUpdatedListener(listener: () -> Unit) {
        presenter.removeOnPagesUpdatedListener(listener)
    }

    /**
     * Add a [CombinedLoadStates] listener to observe the loading state of the current [PagingData].
     *
     * As new [PagingData] generations are submitted and displayed, the listener will be notified to
     * reflect the current [CombinedLoadStates].
     *
     * @param listener [LoadStates] listener to receive updates.
     *
     * @see removeLoadStateListener
     *
     * @sample androidx.paging.samples.addLoadStateListenerSample
     */
    fun addLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        presenter.addLoadStateListener(listener)
    }

    /**
     * Remove a previously registered [CombinedLoadStates] listener.
     *
     * @param listener Previously registered listener.
     * @see addLoadStateListener
     */
    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        presenter.removeLoadStateListener(listener)
    }
}
