import { ConnectionStatus, ProtocolMessage, ProtocolResponse } from '../../shared/types';

export interface GlypdlConnection {
  readonly id: string;
  readonly name: string;
  connect(): Promise<boolean>;
  disconnect(): void;
  isConnected(): boolean;
  getStatus(): Promise<ConnectionStatus>;
  sendMessage(message: ProtocolMessage): Promise<ProtocolResponse>;
}
