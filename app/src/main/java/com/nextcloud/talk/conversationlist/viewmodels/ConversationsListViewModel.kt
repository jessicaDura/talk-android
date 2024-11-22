/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.invitation.data.InvitationsModel
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.users.UserManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class ConversationsListViewModel @Inject constructor(
    private val repository: OfflineConversationsRepository,
    private val chatRepository: ChatMessageRepository,
    var userManager: UserManager
) :
    ViewModel() {

    @Inject
    lateinit var invitationsRepository: InvitationsRepository

    sealed interface ViewState

    object GetRoomsStartState : ViewState
    object GetRoomsErrorState : ViewState
    open class GetRoomsSuccessState(val listIsNotEmpty: Boolean) : ViewState

    private val _getRoomsViewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomsStartState)
    val getRoomsViewState: LiveData<ViewState>
        get() = _getRoomsViewState

    val getRoomsFlow = repository.roomListFlow
        .onEach { list ->
            _getRoomsViewState.value = GetRoomsSuccessState(list.isNotEmpty())
        }.catch {
            _getRoomsViewState.value = GetRoomsErrorState
        }

    object GetFederationInvitationsStartState : ViewState
    object GetFederationInvitationsErrorState : ViewState

    open class GetFederationInvitationsSuccessState(val showInvitationsHint: Boolean) : ViewState

    private val _getFederationInvitationsViewState: MutableLiveData<ViewState> =
        MutableLiveData(GetFederationInvitationsStartState)
    val getFederationInvitationsViewState: LiveData<ViewState>
        get() = _getFederationInvitationsViewState

    object ShowBadgeStartState : ViewState
    object ShowBadgeErrorState : ViewState
    open class ShowBadgeSuccessState(val showBadge: Boolean) : ViewState

    private val _showBadgeViewState: MutableLiveData<ViewState> = MutableLiveData(ShowBadgeStartState)
    val showBadgeViewState: LiveData<ViewState>
        get() = _showBadgeViewState

    fun getFederationInvitations() {
        _getFederationInvitationsViewState.value = GetFederationInvitationsStartState
        _showBadgeViewState.value = ShowBadgeStartState

        userManager.users.blockingGet()?.forEach {
            invitationsRepository.fetchInvitations(it)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(FederatedInvitationsObserver())
        }
    }

    fun getRooms() {
        val startNanoTime = System.nanoTime()
        Log.d(TAG, "fetchData - getRooms - calling: $startNanoTime")
        repository.getRooms()
    }

    fun updateRoomMessages(model: ConversationModel, limit: Int, credentials: String, baseUrl: String) {
        // TODO need to edit fetch init messages to include a limit this is rough
    }

    inner class FederatedInvitationsObserver : Observer<InvitationsModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(invitationsModel: InvitationsModel) {
            val currentUser = userManager.currentUser.blockingGet()

            if (invitationsModel.user.userId?.equals(currentUser.userId) == true &&
                invitationsModel.user.baseUrl?.equals(currentUser.baseUrl) == true
            ) {
                if (invitationsModel.invitations.isNotEmpty()) {
                    _getFederationInvitationsViewState.value = GetFederationInvitationsSuccessState(true)
                } else {
                    _getFederationInvitationsViewState.value = GetFederationInvitationsSuccessState(false)
                }
            } else {
                if (invitationsModel.invitations.isNotEmpty()) {
                    _showBadgeViewState.value = ShowBadgeSuccessState(true)
                }
            }
        }

        override fun onError(e: Throwable) {
            _getFederationInvitationsViewState.value = GetFederationInvitationsErrorState
            Log.e(TAG, "Failed to fetch pending invitations", e)
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationsListViewModel::class.simpleName
    }
}
