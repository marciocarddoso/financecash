export interface NotificationPreferences {
  notifyDueSoonEmail: boolean;
  notifyNegativeBalanceEmail: boolean;
  notifySmsEnabled: boolean;
  phoneNumber: string | null;
}
