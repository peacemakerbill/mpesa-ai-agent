/// Lipa na Bonga is paid by the customer on their own phone with *126#. Safaricom gives merchants no way to ask for it or
/// charge it, so the app and the assistant can only explain the steps. One Bonga Point is worth 20 cents.
const int bongaPointsPerShilling = 5;

/// The steps for paying a Till with Bonga Points. [amount] is optional, and when it is given the points needed are shown.
List<String> bongaSteps(String till, {int amount = 0}) {
  final points = amount > 0 ? ' ($amount, about ${amount * bongaPointsPerShilling} Bonga Points)' : '';
  return [
    'Dial *126# on the Safaricom phone that has the points and choose Lipa na Bonga.',
    'Enter the Till number $till.',
    'Enter the amount in shillings$points.',
    'Enter the *126# Service PIN and confirm. Never share that PIN.',
  ];
}
