package com.assetmanager.flip;

import com.assetmanager.shop.Shop;
import lombok.Value;

/** A shop's usual rates, in tenths of a percent of an item's value. */
@Value
public class ShopRates
{
	int sell;
	int buy;
	int delta;
	/** Whether some items have rates of their own. */
	boolean varies;
	/** Whether the rates include the player's Karamja gloves. */
	boolean gloves;

	public static ShopRates of(Shop shop, boolean hasGloves)
	{
		int sell = shop.getSellMultiplier();
		int buy = shop.getBuyMultiplier();
		boolean varies = false;
		Integer glovesSell = null;
		Integer glovesBuy = hasGloves ? shop.getGlovesBuyMultiplier() : null;
		for (Shop.Item item : shop.getItems().values())
		{
			varies |= item.getM() != null || item.getSm() != null || item.getD() != null;
			if (hasGloves)
			{
				if (glovesSell == null && item.getGs() != null)
				{
					glovesSell = item.getGs();
				}
				if (glovesBuy == null && item.getG() != null)
				{
					glovesBuy = item.getG();
				}
			}
		}
		boolean usesGloves = glovesSell != null || glovesBuy != null;
		return new ShopRates(
			glovesSell != null ? glovesSell : sell,
			glovesBuy != null && buy > 0 ? glovesBuy : buy,
			shop.getDelta(), varies, usesGloves);
	}
}
