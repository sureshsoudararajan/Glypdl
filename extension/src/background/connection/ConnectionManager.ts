import { createDownloadMessage, createPingMessage } from '../../shared/protocol';
import { ConnectionStatus, MediaItem, ProtocolMessage, ProtocolResponse } from '../../shared/types';
import { FlatpakPortalConnection } from './FlatpakPortalConnection';
import { GlypdlConnection } from './GlypdlConnection';
import { NativeMessagingConnection } from './NativeMessagingConnection';

export class ConnectionManager {
  private activeConnection: GlypdlConnection;

  constructor() {
    this.activeConnection = new NativeMessagingConnection();
  }

  async testConnection(): Promise<ConnectionStatus> {
    const isConnected = await this.activeConnection.connect();
    if (!isConnected) {
      // Try Flatpak connection fallback
      const flatpak = new FlatpakPortalConnection();
      const flatpakOk = await flatpak.connect();
      if (flatpakOk) {
        this.activeConnection = flatpak;
      }
    }
    return this.activeConnection.getStatus();
  }

  async getStatus(): Promise<ConnectionStatus> {
    return this.activeConnection.getStatus();
  }

  async sendDownload(
    item: MediaItem,
    autoDownload = false,
    cookiesTxt?: string,
    isTempCookie?: boolean
  ): Promise<ProtocolResponse> {
    const msg = createDownloadMessage(item, autoDownload, cookiesTxt, isTempCookie);
    return this.activeConnection.sendMessage(msg);
  }

  async sendCustomMessage(msg: ProtocolMessage): Promise<ProtocolResponse> {
    return this.activeConnection.sendMessage(msg);
  }
}
