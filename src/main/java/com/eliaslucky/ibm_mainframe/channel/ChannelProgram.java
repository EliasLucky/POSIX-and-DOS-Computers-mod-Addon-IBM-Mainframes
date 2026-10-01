package com.eliaslucky.ibm_mainframe.channel;

import java.util.List;

/**
 * A channel program: an ordered list of CCWs. Executed by the channel
 * against a specific {@link ChannelDevice}, starting at index 0 and
 * running until a command without the chain flag completes.
 */
public record ChannelProgram(List<ChannelCommand> commands) {
    public ChannelProgram {
        commands = List.copyOf(commands);
    }

    public static ChannelProgram of(ChannelCommand... cmds) {
        return new ChannelProgram(List.of(cmds));
    }
}
