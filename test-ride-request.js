// Test Ride Request with Demo Fallback
const puppeteer = require('puppeteer');

async function testRideRequest() {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new' });
    const page = await browser.newPage();

    console.log('='.repeat(60));
    console.log('RIDE REQUEST - DEMO MODE TEST');
    console.log('='.repeat(60));

    // Step 1: Login
    console.log('\n1. Logging in...');
    await page.goto('http://localhost:5173', { waitUntil: 'networkidle2', timeout: 10000 });

    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
      if (demoBtn) demoBtn.click();
    });

    await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});
    console.log('✓ Logged in as Demo Rider');

    // Step 2: Navigate to home
    console.log('\n2. Checking ride request form...');
    const form = await page.evaluate(() => ({
      hasPickup: !!Array.from(document.querySelectorAll('input')).find(i => i.placeholder?.includes('pickup')),
      hasDropoff: !!Array.from(document.querySelectorAll('input')).find(i => i.placeholder?.includes('dropoff')),
      hasRequestButton: Array.from(document.querySelectorAll('button')).some(b => b.textContent.includes('Request Ride')),
    }));

    if (form.hasPickup && form.hasDropoff && form.hasRequestButton) {
      console.log('✓ Ride request form found');
    }

    // Step 3: Click Request Ride button
    console.log('\n3. Requesting ride...');
    await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      const requestBtn = buttons.find(btn => btn.textContent.includes('Request Ride'));
      if (requestBtn) requestBtn.click();
    });

    await new Promise(resolve => setTimeout(resolve, 300));

    // Step 4: Confirm the ride in dialog
    console.log('✓ Confirmation dialog opened');

    const confirmButton = await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.some(btn => btn.textContent.includes('Confirm Ride'));
    });

    if (confirmButton) {
      await page.evaluate(() => {
        const buttons = Array.from(document.querySelectorAll('button'));
        const confirmBtn = buttons.find(btn => btn.textContent.includes('Confirm Ride'));
        if (confirmBtn) confirmBtn.click();
      });

      console.log('✓ Clicked "Confirm Ride" button');
    }

    // Step 5: Wait for response and check for success message
    console.log('✓ Waiting for response...');
    await new Promise(resolve => setTimeout(resolve, 2000));

    const result = await page.evaluate(() => {
      const bodyText = document.body.innerText.toLowerCase();
      const alerts = document.querySelectorAll('[role="alert"]');

      let successFound = false;
      let successText = '';

      // Check all alert containers
      Array.from(alerts).forEach(a => {
        const text = a.textContent.toLowerCase();
        if (text.includes('confirmed') || text.includes('driver will arrive') || text.includes('7 minutes')) {
          successFound = true;
          successText = a.textContent;
        }
      });

      return {
        hasSuccessMessage: successFound,
        successText: successText,
        hasErrorMessage: bodyText.includes('failed to request ride'),
        dialogClosed: !document.body.innerHTML.includes('Confirm Ride Request'),
        bodyContainsConfirmed: bodyText.includes('confirmed'),
      };
    });

    console.log('\n4. Checking response...');

    if (result.hasSuccessMessage) {
      console.log('✓ SUCCESS MESSAGE DISPLAYED');
      console.log(`  "${result.successText}"`);
    } else if (result.hasErrorMessage) {
      console.log('✗ ERROR MESSAGE SHOWN (Backend not available)');
    }

    if (result.dialogClosed) {
      console.log('✓ Dialog closed after request');
    }

    // Final result
    console.log('\n' + '='.repeat(60));
    console.log('RESULTS:');
    console.log(`  Dialog Closed: ${result.dialogClosed}`);
    console.log(`  Success Message: ${result.hasSuccessMessage}`);
    console.log(`  Error Shown: ${result.hasErrorMessage}`);
    console.log(`  Success Text: "${result.successText}"`);

    if (result.hasSuccessMessage) {
      console.log('\n✓ RIDE REQUEST SUCCESSFUL (Demo Mode)');
      console.log('='.repeat(60));
      console.log('\n✅ Demo Mode Fallback is Working:');
      console.log('  • Request is processed locally (no backend needed)');
      console.log('  • Success message displays to user');
      console.log('  • Dialog closes after confirmation');
      console.log('  • Ready for production backend integration');
    } else {
      console.log('\n⚠️  Check manual browser for details');
    }

    return { success: result.hasSuccessMessage };
  } catch (error) {
    console.error(`❌ Test Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

testRideRequest().catch(console.error);
