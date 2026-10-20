import { logger } from "firebase-functions";
import { GoogleAuth } from "google-auth-library";

/** The Pub/Sub topic the project's budget posts to; set on the budget in the Cloud console. */
export const BILLING_TOPIC = "billing-cap";

/** A Cloud Billing budget notification: what's been spent this month against the budget. */
export interface BudgetNotification {
  costAmount: number;
  budgetAmount: number;
  budgetDisplayName?: string;
  currencyCode?: string;
}

/** True once this month's spending has reached the budget. */
export const overBudget = (notice: BudgetNotification): boolean =>
  notice.budgetAmount > 0 && notice.costAmount >= notice.budgetAmount;

/**
 * Switches billing off for [projectId], which stops everything paid: Google's documented way to
 * cap costs. The functions stop answering until billing is switched back on in the console.
 * Does nothing if billing is already off. Needs the Cloud Billing API, and the functions' service
 * account allowed to change the project's billing (Billing Account Administrator).
 */
export async function stopBilling(projectId: string, auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/cloud-billing"] })): Promise<boolean> {
  const client = await auth.getClient();
  const url = `https://cloudbilling.googleapis.com/v1/projects/${projectId}/billingInfo`;
  const info = await client.request<{ billingEnabled?: boolean }>({ url });
  if (!info.data.billingEnabled) {
    logger.info("Budget reached, but billing is already off", { projectId });
    return false;
  }
  await client.request({ url, method: "PUT", data: { billingAccountName: "" } });
  logger.error("Budget reached: billing switched off. Switch it back on in the console to restart the game.", { projectId });
  return true;
}
