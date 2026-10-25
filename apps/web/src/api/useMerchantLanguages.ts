import { useQuery } from "@tanstack/react-query";

import { LANGUAGES } from "../i18n";
import { useApi } from "./ApiContext";

/**
 * The languages a merchant fills in texts for: only the market's language unless platform admins
 * switched on MERCHANT_MULTILINGUAL. Also only the market's language while the switches load or fail.
 */
export function useMerchantLanguages(primaryLocale: string) {
  const api = useApi();
  const features = useQuery({
    queryKey: ["features"],
    queryFn: () => api.features(),
    staleTime: 5 * 60 * 1000,
  });
  const multilingual = features.data?.MERCHANT_MULTILINGUAL === true;
  const primary = LANGUAGES.filter((l) => l.code === primaryLocale);
  return {
    languages: multilingual || primary.length === 0 ? LANGUAGES : primary,
    multilingual,
  };
}
