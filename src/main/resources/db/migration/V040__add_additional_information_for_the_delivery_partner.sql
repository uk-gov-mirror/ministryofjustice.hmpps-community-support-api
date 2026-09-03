-- V40 add additional information for the delivery partner columns

ALTER TABLE referral
    ADD COLUMN IF NOT EXISTS has_additonal_information_for_delivery_partner BOOLEAN,
    ADD COLUMN IF NOT EXISTS additonal_information_for_delivery_partner     TEXT;

COMMENT ON COLUMN referral.has_additonal_information_for_delivery_partner IS 'Whether there is additional information for the delivery partner or not';
COMMENT ON COLUMN referral.additonal_information_for_delivery_partner IS 'The additional information entered for the delivery partner';


