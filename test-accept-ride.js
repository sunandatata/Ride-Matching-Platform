// Test Accept Ride Flow
const puppeteer = require('puppeteer');

async function testAcceptRide() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new', args: ['--no-sandbox'] });
    const riderPage = await browser.newPage();
    const driverPage = await browser.newPage();

    console.log('='.repeat(60));
    console.log('ACCEPT RIDE - COMPLETE FLOW TEST');
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

    // Step 4: Wait for ride to appear in driver app
    console.log('\n4. Waiting for ride to appear in driver app...');
    await new Promise(resolve => setTimeout(resolve, 5000));

    // Step 5: Driver accepts ride
    console.log('\n5. Driver accepting ride...');
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

    // Step 6: Check if rider sees the ride in progress
    console.log('\n6. Checking if rider sees ride in progress...');
    await new Promise(resolve => setTimeout(resolve, 10000)); // Wait for polling (1 sec interval x 10)

    const riderSeesRide = await riderPage.evaluate(() => {
      return {
        hasRideInProgress: document.body.innerText.includes('Ride In Progress'),
        hasDriverName: document.body.innerText.includes('Driver') || document.body.innerText.includes('John'),
        hasETA: document.body.innerText.includes('ETA') || document.body.innerText.includes('minutes'),
        bodyText: document.body.innerText.substring(0, 500),
      };
    });

    console.log('Rider App Status:');
    if (riderSeesRide.hasRideInProgress) {
      console.log('  ✓ Sees "Ride In Progress"');
    } else {
      console.log('  ✗ Does not see "Ride In Progress"');
    }

    if (riderSeesRide.hasDriverName) {
      console.log('  ✓ Sees driver information');
    }

    if (riderSeesRide.hasETA) {
      console.log('  ✓ Sees ETA');
    }

    // Debug: show first part of page
    if (!riderSeesRide.hasRideInProgress && riderSeesRide.hasETA) {
      console.log('\n  → Debug: First 500 chars of rider page:');
      console.log('---');
      console.log(riderSeesRide.bodyText.substring(0, 500));
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
    if (driverState.hasCurrentRide) {
      console.log('  ✓ Shows "Current Ride"');
    }
    if (driverState.hasAvailableRides) {
      console.log('  ✓ Still shows "Available Rides"');
    }

    // Final result
    console.log('\n' + '='.repeat(60));
    if (riderSeesRide.hasRideInProgress && driverState.hasCurrentRide) {
      console.log('✓ ACCEPT RIDE FLOW SUCCESSFUL');
      console.log('='.repeat(60));
      console.log('\nRide Acceptance Working:');
      console.log('  • Driver clicks Accept Ride');
      console.log('  • Confirmation dialog appears');
      console.log('  • Driver confirms acceptance');
      console.log('  • Rider sees "Ride In Progress"');
      console.log('  • Driver sees current ride');
    } else {
      console.log('⚠️  ACCEPT RIDE FLOW INCOMPLETE');
      console.log('='.repeat(60));
      console.log('\nStatus:');
      console.log(`  Dialog appeared: ${dialogVisible}`);
      console.log(`  Rider sees ride: ${riderSeesRide.hasRideInProgress}`);
      console.log(`  Driver sees current: ${driverState.hasCurrentRide}`);
    }

    return { success: riderSeesRide.hasRideInProgress && driverState.hasCurrentRide };
  } catch (error) {
    console.error(`❌ Test Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

testAcceptRide().catch(console.error);
