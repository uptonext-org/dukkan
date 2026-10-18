import { StyleSheet, Text, View } from 'react-native';

import { colors, spacing, typography } from '../../../shared/theme';
import { Button, ListRow, Screen } from '../../../shared/ui';
import type { TabScreenProps } from '../../../navigation/types';
import { useAuth } from '../../auth/AuthProvider';
import { useMe } from '../../auth/api/useMe';

export function ProfileScreen({ navigation }: TabScreenProps<'Profile'>) {
  const { logout } = useAuth();
  const me = useMe();

  return (
    <Screen scroll padded={false}>
      <View style={styles.header}>
        {me.data ? (
          <>
            <Text style={styles.name} accessibilityRole="header">
              {me.data.firstName} {me.data.lastName}
            </Text>
            <Text style={styles.email}>{me.data.email}</Text>
          </>
        ) : (
          <Text style={styles.email}>
            {me.isError ? 'Could not load your profile.' : 'Loading your profile…'}
          </Text>
        )}
      </View>
      <ListRow
        title="Addresses"
        subtitle="Manage delivery addresses"
        onPress={() => navigation.navigate('AddressList')}
      />
      <ListRow
        title="Orders"
        subtitle="View and cancel your orders"
        onPress={() => navigation.navigate('OrderList')}
      />
      <ListRow
        title="Offers"
        subtitle="Current promotions"
        onPress={() => navigation.navigate('Promotions')}
      />
      <View style={styles.logout}>
        <Button title="Sign out" variant="danger" onPress={() => void logout()} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { padding: spacing.lg },
  name: { ...typography.title, color: colors.text },
  email: { ...typography.body, color: colors.textMuted, marginTop: spacing.xs },
  logout: { padding: spacing.lg },
});
