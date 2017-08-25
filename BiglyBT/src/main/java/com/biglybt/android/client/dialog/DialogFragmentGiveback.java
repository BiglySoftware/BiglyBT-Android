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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.biglybt.android.client.*;
import com.biglybt.android.client.activity.ActivityResultHandler;
import com.biglybt.android.client.billing.*;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class DialogFragmentGiveback
	extends DialogFragmentBase
{

	// @formatter:off
	private static final byte[] BILLING_PUBLIC_KEY =  {
		48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 
		3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -98, -92, 108, 
		125, -30, 17, -32, 16, 27, 91, -3, -52, 112, -75, 11, 66, -99, -47, 
		-41, 99, -56, 70, -86, 56, 0, -55, 5, -109, -69, 34, 60, -58, -111, 
		86, 48, 93, 6, 65, 8, -126, 10, -113, 30, 76, -57, -68, 9, 125, -65, 
		122, 22, -57, 46, 2, 87, -90, -75, 62, -123, 103, -68, 72, 35, 26, -109, 
		77, 14, 97, -108, -103, -73, -101, 15, -89, 111, 0, 76, -90, 18, 87, -103, 
		-12, 79, -33, -109, 93, -83, 56, -80, -124, 87, -31, 61, 37, -26, 2, -57, 
		48, 72, 11, 17, 69, 77, -44, 58, -35, -102, 39, -68, 27, -82, -61, 85, -36, 
		-126, 66, 27, 50, 25, -85, 60, 50, -1, 13, -43, 71, 119, -116, -31, 98, 
		-22, -34, 51, 117, -112, 61, 95, -31, 76, -97, -101, -110, 33, 59, -112, 
		94, 121, 121, 65, 65, 125, 66, -80, -1, 115, 5, -20, -20, -98, 17, 70, 
		-46, -105, 117, 41, 73, 100, 2, -9, 126, 74, 86, -31, -112, 38, -53, 8, 
		-121, 31, -31, -101, -123, 87, -19, 72, -112, 50, -26, 45, -15, -62, 116, 
		-90, 79, 78, -57, -81, -42, -2, -101, 23, 44, 29, 76, -92, -46, 32, -27, 
		95, -18, 127, -56, 7, -6, 120, 107, 126, 105, 12, 65, 126, 67, 50, -50, 
		108, -97, -33, 96, -108, -100, -25, 99, 110, -32, 16, -80, -76, 52, -47, 
		-33, 60, -26, -21, 74, 79, 85, 54, 53, -123, -6, -107, 51, 112, 102, 
		-104, -99, 2, 3, 1, 0, 1 };
	// @formatter:on

	private static final String TAG = "GiveBack";

	private static final String ID_USERINVOKED = "UserInvoked";

	private static final String ID_SOURCE = "Source";

	private static final String ID_ANYPURCHASED = "AnyPurchased";

	public static IabHelper iabHelper;

	private static List<SkuDetails> listSkuDetails = new ArrayList<>();

	private RecyclerView listview;

	private GiveBackArrayAdapter adapter;

	private AlertDialog alertDialog;

	private TextView tvBlurb;

	private String source;

	public static void openDialog(Context context, final FragmentManager fm,
			final boolean userInvoked, final String source) {
		iabHelper = new IabHelper(context,
				Base64.encodeToString(BILLING_PUBLIC_KEY, Base64.DEFAULT));

		iabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
			@Override
			public void onIabSetupFinished(IabResult result) {
				if (!result.isSuccess()) {
					// Oh no, there was a problem.
					Log.d(TAG, "Problem setting up In-app Billing: " + result);
				} else {
					iabReady(fm, userInvoked, source);
				}
			}
		});
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		AndroidUtilsUI.AlertDialogBuilder alertDialogBuilder = AndroidUtilsUI.createAlertDialogBuilder(
				getActivity(), R.layout.frag_giveback);

		View view = alertDialogBuilder.view;
		Builder builder = alertDialogBuilder.builder;

		Bundle args = getArguments();
		boolean userInvoked = args.getBoolean(ID_USERINVOKED);
		boolean anyPurchased = args.getBoolean(ID_ANYPURCHASED);
		source = args.getString(ID_SOURCE);

		tvBlurb = (TextView) view.findViewById(R.id.giveback_blurb);
		tvBlurb.setText(anyPurchased ? R.string.giveback_already_subscribed
				: R.string.giveback_consider_subscription);
		AndroidUtilsUI.linkify(tvBlurb);

		listview = (RecyclerView) view.findViewById(R.id.giveback_listview);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			listview.setNestedScrollingEnabled(false);
		}

		adapter = new GiveBackArrayAdapter(getContext(), listSkuDetails);

		listview.setLayoutManager(new LinearLayoutManager(getContext()));
		listview.setAdapter(adapter);

		// Add action buttons
		builder.setPositiveButton(
				anyPurchased ? android.R.string.ok : R.string.later,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
					}
				});

		if (!userInvoked) {
			builder.setNegativeButton(R.string.no_thanks,
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							BiglyBTApp.getAppPreferences().setNeverAskGivebackAgain();
						}
					});
		}
		alertDialog = builder.create();
		return alertDialog;
	}

	private static void iabReady(final FragmentManager fm,
			final boolean userInvoked, final String source) {
		final List<String> additionalSkuList = new ArrayList();
		for (int i = 1; i < 10; i++) {
			additionalSkuList.add("biglybt_test" + i);
		}
		try {
			iabHelper.queryInventoryAsync(true, null, additionalSkuList,
					new IabHelper.QueryInventoryFinishedListener() {

						@Override
						public void onQueryInventoryFinished(IabResult result,
								Inventory inv) {

							if (result.isFailure()) {
								// handle error
								return;
							}

							boolean anyPurchased = false;

							listSkuDetails.clear();
							for (String sku : additionalSkuList) {
								if (inv.hasPurchase(sku)) {
									Purchase invPurchase = inv.getPurchase(sku);
									if (AndroidUtils.DEBUG) {
										Log.d(TAG, invPurchase.toString());

									}
									if (invPurchase.isAutoRenewing()) {
										anyPurchased = true;
										break;
									}
								}

								SkuDetails skuDetails = inv.getSkuDetails(sku);
								if (skuDetails != null
										&& !skuDetails.getTitle().startsWith("!")) {
									listSkuDetails.add(skuDetails);
									if (AndroidUtils.DEBUG) {
										Log.d(TAG, skuDetails.toString());
									}
								}
							}

							if (AndroidUtils.DEBUG) {
								Log.d(TAG, "AnyPurchased? " + anyPurchased);
							}
							if (!anyPurchased || userInvoked) {
								DialogFragmentGiveback dlg = new DialogFragmentGiveback();
								Bundle bundle = new Bundle();
								bundle.putBoolean(ID_USERINVOKED, userInvoked);
								bundle.putBoolean(ID_ANYPURCHASED, anyPurchased);
								bundle.putString(ID_SOURCE, source);
								dlg.setArguments(bundle);
								AndroidUtilsUI.showDialog(dlg, fm, TAG);
							}
						}
					});
		} catch (IabHelper.IabAsyncInProgressException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		try {
			if (iabHelper != null) {
				iabHelper.dispose();
				iabHelper = null;
			}
		} catch (IabHelper.IabAsyncInProgressException e) {
		}
		super.onDestroy();
	}

	@Override
	public String getLogTag() {
		return TAG;
	}

	public class GiveBackArrayAdapter
		extends RecyclerView.Adapter<GiveBackArrayAdapter.ViewHolder>
	{
		private final List<SkuDetails> list;

		private final Context context;

		public class ViewHolder
			extends RecyclerView.ViewHolder
		{

			public ViewHolder(View itemView) {
				super(itemView);

				itemView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {

						SkuDetails skuDetails = list.get(getAdapterPosition());

						try {
							iabHelper.launchPurchaseFlow(getActivity(), skuDetails.getSku(),
									ActivityResultHandler.PURCHASE_RESULTCODE,
									new IabHelper.OnIabPurchaseFinishedListener() {

										@Override
										public void onIabPurchaseFinished(IabResult result,
												Purchase info) {
											if (result.isFailure()) {
												Log.d(TAG, "Error purchasing: " + result);
											}
											if (AndroidUtils.DEBUG) {
												Log.d(TAG, result.toString());
											}

											alertDialog.dismiss();
										}
									}, source);
						} catch (IabHelper.IabAsyncInProgressException e) {
							e.printStackTrace();
						}
					}
				});

			}
		}

		public GiveBackArrayAdapter(Context context, List<SkuDetails> list) {
			super();
			this.context = context;
			this.list = list;
		}

		@Override
		public GiveBackArrayAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
				int viewType) {
			View v = LayoutInflater.from(parent.getContext()).inflate(
					R.layout.row_giveback, parent, false);
			ViewHolder viewHolder = new ViewHolder(v);
			return viewHolder;
		}

		@Override
		public int getItemCount() {
			return list.size();
		}

		@Override
		public void onBindViewHolder(GiveBackArrayAdapter.ViewHolder holder,
				int position) {
			View rowView = holder.itemView;
			TextView tvTitle = (TextView) rowView.findViewById(R.id.giveback_title);
			TextView tvSubtitle = (TextView) rowView.findViewById(
					R.id.giveback_subtitle);
			TextView tvPrice = (TextView) rowView.findViewById(R.id.giveback_price);

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
					"years",
					"months",
					"weeks",
					"days"
				};
				String[] singularKeys = {
					"year",
					"month",
					"week",
					"day"
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
					int textId = Resources.getSystem().getIdentifier(key, "string",
							"android");
					String unitText = textId == 0 ? context.getString(fallbackTextId)
							: Resources.getSystem().getString(textId);
					price += unitText;
				}
			}

			tvPrice.setText(price);

		}
	}

	int[] getPeriodYMWD(String subscriptionPeriod) {
		final Pattern PATTERN = Pattern.compile(
				"([-+]?)P(?:([-+]?[0-9]+)Y)?(?:([-+]?[0-9]+)M)?(?:([-+]?[0-9]+)W)?(?:([-+]?[0-9]+)D)?",
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
