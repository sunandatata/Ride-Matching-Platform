// Test Accept Ride Flow - Debug Version
const puppeteer = require('puppeteer');

async function testAcceptRide() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
    const riderPage = await browser.newPage();
    const driverPage = await browser.newPage();

    console.log('='.repeat(60));
    console.log('ACCEPT RIDE - DEBUG TEST');
    console.log('='.repeat(60));

    // Step 1: Login both
    console.log('\n1. Logging in...');
    await riderPage.goto('http://localhost:5173', { waitUntil: 'networkidle2', timeout: 10000 });
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await riderPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Rider logged in');

    await driverPage.goto('http://localhost:3002', { waitUntil: 'networkidle2', timeout: 10000 });
    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });
    await driverPage.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Driver logged in');

    // Step 2: Driver goes online
    console.log('\n2. Driver going online...');
    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const onlineBtn = buttons.find(btn => btn.textContent.includes('Go Online'));
      if (onlineBtn) onlineBtn.click();
    });
    await new Promise(resolve => setTimeout(resolve, 1000));
    console.log('✓ Driver online');

    // Step 3: Rider requests a ride
    console.log('\n3. Rider requesting ride...');
    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const requestBtn = buttons.find(btn => btn.textContent.includes('Request Ride'));
      if (requestBtn) requestBtn.click();
    });
    await new Promise(resolve => setTimeout(resolve, 500));

    await riderPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const confirmBtn = buttons.find(btn => btn.textContent.includes('Confirm Ride'));
      if (confirmBtn) confirmBtn.click();
    });
    console.log('✓ Ride requested');

    // Check shared storage server immediately after request
    const sharedRidesAfterRequest = await (async () => {
      try {
        const response = await fetch('http://localhost:3001/api/shared/available-rides');
        return response.ok ? await response.json() : [];
      } catch (e) {
        console.error('Failed to fetch shared rides:', e);
        return [];
      }
    })();
    console.log(`  → Rides in shared storage: ${sharedRidesAfterRequest.length}`);
    if (sharedRidesAfterRequest.length > 0) {
      console.log(`    First ride ID: ${sharedRidesAfterRequest[0].id}`);
    }

    // Step 4: Wait for ride to appear in driver app
    console.log('\n4. Waiting for ride to appear in driver app...');
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Step 5: Driver accepts ride
    console.log('\n5. Driver accepting ride...');
    const rideIdBeforeAccept = await driverPage.evaluate(() => {
      const cards = document.querySelectorAll('.ride-card');
      if (cards.length > 0) {
        const firstCard = cards[0];
        return firstCard.textContent;
      }
      return null;
    });
    console.log(`  → Found ride card: ${rideIdBeforeAccept ? 'Yes' : 'No'}`);

    await driverPage.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const acceptBtn = buttons.find(btn => btn.textContent.includes('ACCEPT RIDE'));
      if (acceptBtn) {
        console.log('Found Accept button, clicking...');
        acceptBtn.click();
      }
    });

    // Wait for dialog to appear
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Check if dialog appeared
    const dialogVisible = await driverPage.evaluate(() => {
      return document.body.innerText.includes('Accept This Ride');
    });

    if (dialogVisible) {
      console.log('✓ Confirmation dialog appeared');

      // Confirm in dialog
      await driverPage.evaluate(() => {
        const buttons = Array.from(document.querySelectorAll('button'));
        const confirmBtn = buttons.find(btn => btn.textContent.includes('Accept') && btn.textContent.length < 20);
        if (confirmBtn) {
          console.log('Confirming in dialog...');
          confirmBtn.click();
        }
      });

      console.log('✓ Ride confirmed by driver');
    } else {
      console.log('⚠️  Dialog did not appear');
    }

    // Wait for mutation to complete (API timeout + processing)
    console.log('  → Waiting 2.5 seconds for mutation to complete...');
    await new Promise(resolve => setTimeout(resolve, 2500));

    // Check shared storage server after acceptance
    console.log('\n  → Checking shared storage after acceptance...');
    const sharedRidesAfterAccept = await (async () => {
      try {
        const response = await fetch('http://localhost:3001/api/shared/available-rides');
        return response.ok ? await response.json() : [];
      } catch (e) {
        console.error('Failed to fetch shared rides:', e);
        return [];
      }
    })();
    console.log(`    Rides still in available: ${sharedRidesAfterAccept.length}`);

    const currentRideData = await (async () => {
      try {
        const response = await fetch('http://localhost:3001/api/shared/current-ride');
        return response.ok ? await response.json() : null;
      } catch (e) {
        console.error('Failed to fetch current ride:', e);
        return null;
      }
    })();
    console.log(`    Current ride saved: ${currentRideData ? 'Yes' : 'No'}`);
    if (currentRideData) {
      console.log(`      Driver name: ${currentRideData.driver_name}`);
      console.log(`      Status: ${currentRideData.status}`);
    }

    // Step 6: Check if rider sees the ride in progress
    console.log('\n6. Checking if rider sees ride in progress...');
    console.log('  → Waiting 3 seconds for polling...');
    await new Promise(resolve => setTimeout(resolve, 3000)); // Wait for sync

    // Check rider app's current ride from shared storage server
    const riderCurrentRideFromStorage = await (async () => {
      try {
        const response = await fetch('http://localhost:3001/api/shared/current-ride');
        return response.ok ? await response.json() : null;
      } catch (e) {
        console.error('Failed to fetch current ride:', e);
        return null;
      }
    })();
    console.log(`  → Current ride in shared storage: ${riderCurrentRideFromStorage ? 'Yes' : 'No'}`);

    const riderSeesRide = await riderPage.evaluate(() => {
      return {
        hasRideInProgress: document.body.innerText.includes('Ride In Progress'),
        hasDriverName: document.body.innerText.includes('Driver') || document.body.innerText.includes('John'),
        hasETA: document.body.innerText.includes('ETA') || document.body.innerText.includes('minutes'),
        bodyText: document.body.innerText.substring(0, 800),
      };
    });

    console.log('Rider App Status:');
    console.log(`  ETA visible: ${riderSeesRide.hasETA}`);
    console.log(`  "Ride In Progress" visible: ${riderSeesRide.hasRideInProgress}`);
    console.log(`  Driver info visible: ${riderSeesRide.hasDriverName}`);

    if (!riderSeesRide.hasRideInProgress) {
      console.log('\n  → First 800 chars of rider page:');
      console.log('---');
      console.log(riderSeesRide.bodyText);
      console.log('---');
    }

    // Check driver app state
    const driverState = await driverPage.evaluate(() => {
      return {
        hasCurrentRide: document.body.innerText.includes('Current Ride'),
        hasAvailableRides: document.body.innerText.includes('Available Rides'),
      };
    });

    console.log('\nDriver App Status:');
    console.log(`  Shows "Current Ride": ${driverState.hasCurrentRide}`);
    console.log(`  Shows "Available Rides": ${driverState.hasAvailableRides}`);

    // Final result
    console.log('\n' + '='.repeat(60));
    if (riderSeesRide.hasRideInProgress && driverState.hasCurrentRide) {
      console.log('✓ ACCEPT RIDE FLOW SUCCESSFUL');
    } else {
      console.log('⚠️  ACCEPT RIDE FLOW INCOMPLETE');
      console.log('\nDebug Info:');
      console.log(`  Rider sees "Ride In Progress": ${riderSeesRide.hasRideInProgress}`);
      console.log(`  Driver sees "Current Ride": ${driverState.hasCurrentRide}`);
      console.log(`  Shared current_ride exists: ${riderCurrentRideFromStorage !== null}`);
    }
    console.log('='.repeat(60));

    return { success: riderSeesRide.hasRideInProgress && driverState.hasCurrentRide };
  } catch (error) {
    console.error(`❌ Test Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

testAcceptRide().catch(console.error);
