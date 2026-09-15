package com.frxe.music.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class FrxeSystemMediaPlayer(
    player: Player,
    private val queueAvailability: () -> SystemMediaQueueAvailability,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit
) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        val availability = queueAvailability()
        val commands = state.availableCommands.buildUpon()

        commands.set(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            availability.previous
        )
        commands.set(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            availability.previous
        )
        commands.set(
            Player.COMMAND_SEEK_TO_NEXT,
            availability.next
        )
        commands.set(
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            availability.next
        )

        return state
            .buildUpon()
            .setAvailableCommands(commands.build())
            .build()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int
    ): ListenableFuture<*> =
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                onPrevious()
                Futures.immediateVoidFuture()
            }

            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                onNext()
                Futures.immediateVoidFuture()
            }

            else ->
                super.handleSeek(
                    mediaItemIndex,
                    positionMs,
                    seekCommand
                )
        }

    fun refreshQueueCommands() {
        invalidateState()
    }

    private fun Player.Commands.Builder.set(
        command: Int,
        enabled: Boolean
    ) {
        if (enabled) {
            add(command)
        } else {
            remove(command)
        }
    }
}
