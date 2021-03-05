/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.*;
import com.android.billingclient.api.BillingClient.BillingResponseCode;
import com.android.billingclient.api.BillingClient.SkuType;
import com.android.billingclient.api.Purchase.PurchaseState;
import com.android.billingclient.api.Purchase.PurchasesResult;
import com.biglybt.android.client.*;
import com.biglybt.util.Thunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DialogFragmentGiveback
	extends DialogFragmentBase
{

	private static final String TAG = "GiveBack";

	private static final String ID_USERINVOKED = "UserInvoked";

	private static final String ID_SOURCE = "Source";

	private static final String ID_ANYPURCHASED = "AnyPurchased";

	private static final String SKU_PREFIX = "biglybt_2020_";

	@Thunk
	static List<SkuDetails> listSkuDetails = new ArrayList<>();

	@Thunk
	static BillingClient billingClient = null;

	@Thunk
	static AlertDialog alertDialog = null;

	public static void openDialog(final FragmentActivity activity,
			final FragmentManager fm, final boolean userInvoked,
			final String source) {
		billingClient = BillingClient.newBuilder(
				activity).enablePendingPurchases().setListener(
						// This would be better off in the DialogFragment, but we can't
						// setListener after build
						(billingResult, purchases) -> {
							if (AndroidUtils.DEBUG) {
								Log.d(TAG,
										"onPurchasesUpdated: " + billingResult.getResponseCode()
												+ ": " + billingResult.getDebugMessage());
							}
							try {
								if (billingResult.getResponseCode() == BillingResponseCode.OK
										&& purchases != null) {
									verifyPurchases(purchases);

									String sku = "";
									if (purchases.size() == 1) {
										sku = purchases.get(0).getSku();
									}
									AnalyticsTracker.getInstance(activity).sendEvent("Purchase",
											sku, source, null);
								} else {
									AnalyticsTracker.getInstance(activity).logError(
											"Purchase Error " + billingResult.getResponseCode() + ": "
													+ billingResult.getDebugMessage(),
											source);
								}
								alertDialog.dismiss();
							} catch (Throwable ignore) {
							}
						}).build();
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
				int responseCode = billingResult.getResponseCode();
				if (responseCode != BillingResponseCode.OK) {
					// Oh no, there was a problem.
					Log.d(TAG, "Problem setting up In-app Billing: " + responseCode);

					AndroidUtilsUI.showDialog(activity, R.string.giveback_title,
							R.string.giveback_no_google);
				} else {
					// ANR in the wild, in the "Main" thread (MiBox S, Android 9, API 28)
					OffThread.runOffUIThread(() -> billingReady(activity, fm, userInvoked, source));
				}
			}

			@Override
			public void onBillingServiceDisconnected() {

			}
		});
	}

	private static void verifyPurchases(List<Purchase> purchases) {
		for (Purchase purchase : purchases) {
			if (purchase.getPurchaseState() == PurchaseState.PURCHASED
					&& !purchase.isAcknowledged()) {
				billingClient.acknowledgePurchase(
						AcknowledgePurchaseParams.newBuilder().setPurchaseToken(
								purchase.getPurchaseToken()).build(),
						ackResult -> {
							// TODO: Something?
							if (AndroidUtils.DEBUG) {
								Log.d(TAG,
										"onAcknowledgePurchaseResponse: "
												+ ackResult.getResponseCode() + ": "
												+ ackResult.getDebugMessage());
							}
						});
			} else {
				// TODO: Store purchase token, check on next launch (or timer?)
				// for state change so we can't acknowledge and remove
				// token from storage.
			}
		}
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		FragmentActivity activity = requireActivity();
		Context context = requireContext();

		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				activity, R.layout.frag_giveback);

		View view = alertDialogBuilder.view;
		AlertDialog.Builder builder = alertDialogBuilder.builder;

		Bundle args = getArguments();
		assert args != null;
		boolean userInvoked = args.getBoolean(ID_USERINVOKED);
		boolean anyPurchased = args.getBoolean(ID_ANYPURCHASED);
		//String source = args.getString(ID_SOURCE);

		TextView tvBlurb = view.findViewById(R.id.giveback_blurb);
		AndroidUtilsUI.linkify(activity, tvBlurb, null,
				AndroidUtils.getMultiLangString(context,
						anyPurchased ? R.string.giveback_already_subscribed
								: R.string.giveback_consider_subscription,
						"\n"));

		RecyclerView listview = view.findViewById(R.id.giveback_listview);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			listview.setNestedScrollingEnabled(false);
		}

		GiveBackArrayAdapter adapter = new GiveBackArrayAdapter(getContext(),
				listSkuDetails);

		listview.setLayoutManager(new LinearLayoutManager(getContext()));
		listview.setAdapter(adapter);

		// Add action buttons
		builder.setPositiveButton(
				AndroidUtils.getMultiLangString(context,
						anyPurchased ? android.R.string.ok : R.string.later, " / "),
				(dialog, id) -> {
				});

		if (!userInvoked) {
			builder.setNegativeButton(
					AndroidUtils.getMultiLangString(context, R.string.no_thanks, " / "),
					(dialog,
							which) -> BiglyBTApp.getAppPreferences().setNeverAskGivebackAgain());
		}
		alertDialog = builder.create();
		return alertDialog;
	}

	private static boolean areSubscriptionsSupported() {
		if (billingClient == null) {
			return false;
		}
		int responseCode = billingClient.isFeatureSupported(
				BillingClient.FeatureType.SUBSCRIPTIONS).getResponseCode();
		if (responseCode != BillingResponseCode.OK) {
			Log.w(TAG,
					"areSubscriptionsSupported() got an error response: " + responseCode);
		}

		return responseCode == BillingResponseCode.OK;
	}

	@Thunk
	@WorkerThread
	static void billingReady(FragmentActivity activity, final FragmentManager fm,
			final boolean userInvoked, final String source) {

		if (!areSubscriptionsSupported()) {
			AndroidUtilsUI.showDialog(activity, R.string.giveback_title,
					R.string.giveback_no_google);
			return;
		}

		PurchasesResult purchasesResult = billingClient.queryPurchases(
				SkuType.SUBS);
		List<Purchase> purchasesList = purchasesResult.getPurchasesList();
		boolean anyPurchased = purchasesResult.getResponseCode() == BillingResponseCode.OK
				&& purchasesList != null && purchasesList.size() > 0;
		if (anyPurchased) {
			verifyPurchases(purchasesList);

			for (Purchase purchase : purchasesList) {
				if (!purchase.isAutoRenewing()) {
					anyPurchased = false;
					break;
				}
			}

			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "iabReady: anyPurchased?" + anyPurchased + " for "
						+ purchasesList.size());
			}
		}
		final boolean anyPurchasedF = anyPurchased;

		final List<String> additionalSkuList = new ArrayList<>();
		for (int i = 1; i < 10; i++) {
			additionalSkuList.add(SKU_PREFIX + i);
		}
		try {
			SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
			params.setSkusList(additionalSkuList).setType(SkuType.SUBS);
			listSkuDetails.clear();
			billingClient.querySkuDetailsAsync(params.build(),
					(billingResult, skuDetailsList) -> {

						if (billingResult.getResponseCode() != BillingResponseCode.OK
								|| skuDetailsList == null) {
							Log.d(TAG,
									"onSkuDetailsResponse: " + billingResult.getResponseCode()
											+ ": " + billingResult.getDebugMessage() + ", sku="
											+ skuDetailsList);
							return;
						}

						if (!anyPurchasedF) {
							for (SkuDetails skuDetails : skuDetailsList) {
								if (!skuDetails.getTitle().startsWith("*")) {
									listSkuDetails.add(skuDetails);
								}
							}
						}

						if (!anyPurchasedF || userInvoked) {
							DialogFragmentGiveback dlg = new DialogFragmentGiveback();
							Bundle bundle = new Bundle();
							bundle.putBoolean(ID_USERINVOKED, userInvoked);
							bundle.putBoolean(ID_ANYPURCHASED, anyPurchasedF);
							bundle.putString(ID_SOURCE, source);
							dlg.setArguments(bundle);
							AndroidUtilsUI.showDialog(dlg, fm, TAG);
						}
					});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		if (billingClient != null
				&& !requireActivity().isChangingConfigurations()) {
			billingClient.endConnection();
			billingClient = null;
		}
		super.onDestroy();
	}

	public class GiveBackArrayAdapter
		extends RecyclerView.Adapter<GiveBackArrayAdapter.ViewHolder>
	{
		final List<SkuDetails> list;

		private final Context context;

		public class ViewHolder
			extends RecyclerView.ViewHolder
		{

			ViewHolder(View itemView) {
				super(itemView);

				itemView.setOnClickListener(v -> {

					SkuDetails skuDetails = list.get(getBindingAdapterPosition());

					BillingFlowParams params = BillingFlowParams.newBuilder().setSkuDetails(
							skuDetails).build();
					billingClient.launchBillingFlow(requireActivity(), params);
				});

			}
		}

		GiveBackArrayAdapter(Context context, List<SkuDetails> list) {
			super();
			this.context = context;
			this.list = list;
		}

		@NonNull
		@Override
		public GiveBackArrayAdapter.ViewHolder onCreateViewHolder(
				@NonNull ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext()).inflate(
					R.layout.row_giveback, parent, false);
			return new ViewHolder(v);
		}

		@Override
		public int getItemCount() {
			return list.size();
		}

		@Override
		public void onBindViewHolder(
				@NonNull GiveBackArrayAdapter.ViewHolder holder, int position) {
			View rowView = holder.itemView;
			TextView tvTitle = rowView.findViewById(R.id.giveback_title);
			TextView tvSubtitle = rowView.findViewById(R.id.giveback_subtitle);
			TextView tvPrice = rowView.findViewById(R.id.giveback_price);

			SkuDetails item = list.get(position);

			String title = item.getTitle();
			// title in format "Title (Full App Name)"
			int idxChop = title.indexOf('(', 1);
			if (idxChop > 0) {
				title = title.substring(0, idxChop - 1);
			}

			tvTitle.setText(title);
			tvSubtitle.setText(item.getDescription());

			String price = item.getPrice() + "\n" + item.getPriceCurrencyCode();

			String subscriptionPeriod = item.getSubscriptionPeriod();
			// 	java.time.Period not avail until API 26!
			// fake it for ones we might use, assume 1 unit only
			int[] periodYMWD = getPeriodYMWD(subscriptionPeriod);
			if (periodYMWD != null) {
				String[] pluralKeys = {
					"years", //NON-NLS
					"months", //NON-NLS
					"weeks", //NON-NLS
					"days" //NON-NLS
				};
				String[] singularKeys = {
					"year", //NON-NLS
					"month", //NON-NLS
					"week", //NON-NLS
					"day" //NON-NLS
				};
				@StringRes
				int[] fbPluralKeys = {
					R.string.years,
					R.string.months,
					R.string.weeks,
					R.string.days
				};
				@StringRes
				int[] fbSingularKeys = {
					R.string.year,
					R.string.month,
					R.string.week,
					R.string.day
				};

				int keyIndex = -1;
				int count = 0;
				for (int i = 0; i < periodYMWD.length; i++) {
					if (periodYMWD[i] > 0) {
						keyIndex = i;
						count = periodYMWD[i];
						break;
					}
				}

				if (keyIndex >= 0) {
					price += "/";

					String key;
					@StringRes
					int fallbackTextId;

					if (count == 1) {
						key = singularKeys[keyIndex];
						fallbackTextId = fbSingularKeys[keyIndex];
					} else {
						price += count + " ";
						key = pluralKeys[keyIndex];
						fallbackTextId = fbPluralKeys[keyIndex];
					}
					String unitText = AndroidUtils.getSystemString(context, key,
							fallbackTextId);
					price += unitText;
				}
			}

			tvPrice.setText(price);

		}
	}

	@Thunk
	static int[] getPeriodYMWD(String subscriptionPeriod) {
		final Pattern PATTERN = Pattern.compile(
				"([-+]?)P(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?", //NON-NLS
				Pattern.CASE_INSENSITIVE);
		Matcher matcher = PATTERN.matcher(subscriptionPeriod);
		if (matcher.matches()) {
			int negate = ("-".equals(matcher.group(1)) ? -1 : 1);
			String yearMatch = matcher.group(2);
			String monthMatch = matcher.group(3);
			String weekMatch = matcher.group(4);
			String dayMatch = matcher.group(5);
			if (yearMatch != null || monthMatch != null || dayMatch != null
					|| weekMatch != null) {
				try {
					int years = parseNumber(yearMatch, negate);
					int months = parseNumber(monthMatch, negate);
					int weeks = parseNumber(weekMatch, negate);
					int days = parseNumber(dayMatch, negate);
					// Math.multiplyExact API 24
					days += weeks * 7;
					return new int[] {
						years,
						months,
						weeks,
						days
					};
				} catch (NumberFormatException ex) {
					//throw new DateTimeParseException("Text cannot be parsed to a Period", text, 0, ex);
				}
			}
		}
		return null;
	}

	private static int parseNumber(String str, int negate) {
		if (str == null) {
			return 0;
		}
		int val = Integer.parseInt(str);
		try {
			// Math.multiplyExact API 24
			return val * negate;
		} catch (ArithmeticException ex) {
			return 0;
			//throw new DateTimeParseException("Text cannot be parsed to a Period", text, 0, ex);
		}
	}
}
