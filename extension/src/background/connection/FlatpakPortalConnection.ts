import { ConnectionStatus, ProtocolMessage, ProtocolResponse } from '../../shared/types';
import { GlypdlConnection } from './GlypdlConnection';
import { NativeMessagingConnection } from './NativeMessagingConnection';

export class FlatpakPortalConnection implements GlypdlConnection {
  readonly id = 'flatpak-portal';
  readonly name = 'Flatpak Sandbox Native Messaging';

  private nativeConn = new NativeMessagingConnection();

  async connect(): Promise<boolean> {
    return this.nativeConn.connect();
  }

  disconnect(): void {
    this.nativeConn.disconnect();
  }

  isConnected(): boolean {
    return this.nativeConn.isConnected();
  }

  async getStatus(): Promise<ConnectionStatus> {
    const status = await this.nativeConn.getStatus();
    return {
      ...status,
      isFlatpak: true,
      error: status.connected
        ? undefined
        : 'Flatpak Firefox requires host manifest registered at ~/.var/app/org.mozilla.firefox/.mozilla/native-messaging-hosts/'
    };
  }

  async sendMessage(message: ProtocolMessage): Promise<ProtocolResponse> {
    const resp = await this.nativeConn.sendMessage(message);
    if (!resp.success && !resp.connected) {
      return {
        ...resp,
        message:
          'Could not reach Glypdl host from Flatpak Firefox. Ensure the host manifest is registered in Glypdl Preferences.'
      };
    }
    return resp;
  }
}
